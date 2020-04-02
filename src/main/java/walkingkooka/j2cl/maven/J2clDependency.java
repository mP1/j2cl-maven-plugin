/*
 * Copyright 2019 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.j2cl.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a single artifact including the project or any of its dependencies. These will in turn have java files etc.
 */
final class J2clDependency implements Comparable<J2clDependency> {

    /**
     * Retrieves all {@link J2clDependency} in order of leaf to the project itself which should be last.
     */
    static J2clDependency gather(final MavenProject project,
                                 final J2clRequest request) {
        COORD_TO_DEPENDENCY.clear();
        return new J2clDependency(J2clArtifactCoords.with(project.getArtifact()),
                project,
                null,
                request);
    }

    /**
     * Lookup the coords returning the dependency
     */
    static Optional<J2clDependency> get(final J2clArtifactCoords coords) {
        return Optional.ofNullable(COORD_TO_DEPENDENCY.get(coords));
    }

    /**
     * Lookup the coords returning the dependency
     */
    static J2clDependency getOrFail(final J2clArtifactCoords coords) {
        return get(coords)
                .orElseThrow(() -> new IllegalArgumentException("Unknown coords " + CharSequences.quote(coords.toString()) + "\n" + COORD_TO_DEPENDENCY.keySet() + "\n" + COORD_TO_DEPENDENCY));
    }

    private final static Map<J2clArtifactCoords, J2clDependency> COORD_TO_DEPENDENCY = Maps.sorted();

    // ctor.............................................................................................................

    /**
     * Private ctor use public static methods.
     */
    private J2clDependency(final J2clArtifactCoords coords,
                           final MavenProject mavenProject,
                           final J2clPath artifactFile,
                           final J2clRequest request) {
        final Artifact artifact = mavenProject.getArtifact();
        this.coords = coords;
        this.artifact = artifact;
        this.mavenProject = mavenProject;
        this.artifactFile = artifactFile;
        this.request = request;

        if (null != COORD_TO_DEPENDENCY.put(coords, this)) {
            throw new IllegalArgumentException("Duplicate artifact " + CharSequences.quote(coords.toString()));
        }

        this.gatherDeclaredDependencies();
        this.addAddedDependencies();
    }

    private void gatherDeclaredDependencies() {
        final J2clRequest request = this.request();
        final J2clClasspathScope scope = request.scope();

        for (final Artifact artifact : this.mavenProject.getArtifacts()) {
            if (isSystemScope(artifact)) {
                continue;
            }

            if (false == artifact.getScope().equals("provided") && false == scope.scopeFilter().test(artifact)) {
                continue;
            }

            this.dependencyCoords.add(this.getOrCreate(J2clArtifactCoords.with(artifact)).coords());
        }
    }

    private static boolean isSystemScope(final Artifact artifact) {
        return Artifact.SCOPE_SYSTEM.equals(artifact.getScope());
    }

    private void addAddedDependencies() {
        final J2clRequest request = this.request();

        for (final J2clArtifactCoords added : request.addedDependencies(this.coords())) {
            if (false == this.dependencyCoords.contains(added)) {
                this.getOrCreate(added);
                this.dependencyCoords.add(added);
            }
        }
    }

    private J2clDependency getOrCreate(final J2clArtifactCoords coords) {
        return this.getOrCreate0(this.request()
        .replacement(coords)
        .orElse(coords));
    }

    private J2clDependency getOrCreate0(final J2clArtifactCoords coords) {
        final J2clDependency dependency = COORD_TO_DEPENDENCY.get(coords);
        return null != dependency ?
                dependency :
                this.loadDependency(coords);
    }

    private J2clDependency loadDependency(final J2clArtifactCoords coords) {
        if (coords.isWildcardVersion()) {
            throw new IllegalArgumentException("Dependency " + CharSequences.quoteAndEscape(coords.toString()) + " with version not declared, dependencies: " + COORD_TO_DEPENDENCY.keySet());
        }

        final J2clRequest request = this.request();
        final MavenProject project = request.mavenMiddleware()
                .mavenProject(coords.mavenArtifact(request.scope(), this.artifact.getArtifactHandler()));

        return new J2clDependency(coords,
                project,
                request.mavenMiddleware().mavenFile(coords.toString()).orElseThrow(() -> new IllegalArgumentException("Archive file missing for " + CharSequences.quote(coords.toString())))/*.map(J2clPath.with())*/,
                request);
    }

    // dependencies.....................................................................................................

    private final Set<J2clArtifactCoords> dependencyCoords = Sets.sorted();
    
    /**
     * Retrieves all dependencies including transients, and will include any required artifacts.
     */
    Set<J2clDependency> dependencies() {
        if (null == this.dependencies) {
            this.computeTransitiveDependencies();
        }

        return this.dependencies;
    }

    private Set<J2clDependency> dependencies;

    private void computeTransitiveDependencies() {
        final J2clRequest request = this.request();

        final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat = Maps.sorted();
        for(final Entry<J2clArtifactCoords, J2clDependency> coordAndDependency : COORD_TO_DEPENDENCY.entrySet()) {
            flat.put(coordAndDependency.getKey(), coordAndDependency.getValue().dependencyCoords);
        }

        final J2clDependencyGraphCalculator calculator = J2clDependencyGraphCalculator.with(flat,
                request.required()
        .stream()
        .filter(J2clDependency::requiredFilter)
        .collect(Collectors.toCollection(Sets::sorted)));

        final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> tree = calculator.run();

        for(final Entry<J2clArtifactCoords, J2clDependency> coordAndDependency : COORD_TO_DEPENDENCY.entrySet()) {
            final J2clArtifactCoords coords = coordAndDependency.getKey();
            final J2clDependency dependency = coordAndDependency.getValue();

            dependency.dependencies = tree.get(coords)
                    .stream()
                    .map(J2clDependency::getOrFail)
                    .collect(Collectors.toCollection(Sets::sorted));
        }
    }

    private static boolean requiredFilter(final J2clArtifactCoords coords) {
        return false == getOrFail(coords).isIgnored();
    }

    /**
     * Returns the classpath and dependencies in order without any duplicates.
     */
    Set<J2clDependency> classpathAndDependencies() {
        return Sets.readOnly(Stream.concat(this.discoveredBootstrapAndJre(), Stream.concat(this.request().classpathRequired().stream(), this.dependencies().stream()))
                .filter(this::isDifferent)
                .filter(J2clDependency::isClasspathRequired)
                .collect(Collectors.toCollection(Sets::ordered)));
    }

    private Stream<J2clDependency> discoveredBootstrapAndJre() {
        return COORD_TO_DEPENDENCY.values()
                .stream()
                .filter(J2clDependency::isBootstrapAndJreFiles);
    }

    private boolean isDifferent(final J2clDependency other) {
        return 0 != this.compareTo(other);
    }

    // isDependency.....................................................................................................

    boolean isDependency() {
        return null != this.artifactFile;
    }

    // coords...........................................................................................................

    J2clArtifactCoords coords() {
        return this.coords;
    }

    /**
     * The coords containing the groupid, artifact-id, version and classifier
     */
    private final J2clArtifactCoords coords;

    /**
     * Only returns true for artifacts that have been declared as classpath required or identified as an annotation
     * processor archive.
     */
    boolean isClasspathRequired() {
        if (null == this.classpathRequired) {
            final J2clRequest request = this.request();
            final J2clArtifactCoords coords = this.coords();

            this.classpathRequired = request.isClasspathRequired(coords) ||
                    false == request.isJavascriptSourceRequired(coords) ||
                    this.isAnnotationProcessor() ||
                    this.isJreBootstrapClassFiles() ||
                    this.isJreClassFiles();
        }
        return this.classpathRequired;
    }

    private Boolean classpathRequired;

    /**
     * Only returns true for the JRE binary artifact
     */
    boolean isJavascriptSourceRequired() {
        final J2clRequest request = this.request();
        final J2clArtifactCoords coords = this.coords();
        return (request.isJavascriptSourceRequired(coords) || false == request.isClasspathRequired(coords)) &&
            false == this.isAnnotationProcessor() &&
            this.isJavascriptBootstrapFiles() ||
            this.isJavascriptFiles() ||
            false == this.isJreBootstrapClassFiles() &&
            false == this.isJreClassFiles();
    }

    /**
     * Used to test if a dependency should be ignored and the archive files used as they are. Examples of this include
     * the shaded JRE binaries and the jszip form, each used depending whether class files or java source are required.
     */
    boolean isIgnored() {
        if (null == this.ignored) {
            this.ignored = this.request().isIgnored(this.coords) ||
                    this.isAnnotationProcessor() ||
                    this.isJavascriptBootstrapFiles() ||
                    this.isJavascriptFiles() ||
                    this.isJreBootstrapClassFiles() ||
                    this.isJreClassFiles();
        }
        return this.ignored;
    }

    private Boolean ignored;

    // isAnnotationProcessor............................................................................................

    /**
     * Returns true if this dependency includes an annotation processor services file.
     */
    private boolean isAnnotationProcessor() {
        if (null == this.annotationProcessor) {
            this.testArchive();
        }

        return this.annotationProcessor;
    }

    private Boolean annotationProcessor;

    /**
     * Returns true if this dependency is a JRE bootstrap or JRE class files.
     */
    private boolean isBootstrapAndJreFiles() {
        return this.isJreBootstrapClassFiles() ||
            this.isJreClassFiles() ||
            this.isJavascriptBootstrapFiles() ||
            this.isJavascriptFiles();
    }

    // isJavascriptBootstrapFiles..................................................................................................

    /**
     * Returns true if this archive contains JAVASCRIPT bootstrap class files, by testing if java.lang.Class class file exists.
     */
    private boolean isJavascriptBootstrapFiles() {
        if (null == this.javascriptBootstrapFiles) {
            this.testArchive();
        }

        return this.javascriptBootstrapFiles;
    }

    private Boolean javascriptBootstrapFiles;

    // isJavascriptFiles..................................................................................................

    /**
     * Returns true if this archive contains JAVASCRIPT  files, by testing if java.lang.  file exists.
     */
    private boolean isJavascriptFiles() {
        if (null == this.javascriptFiles) {
            this.testArchive();
        }

        return this.javascriptFiles;
    }

    private Boolean javascriptFiles;

    // isJreBootstrapClassFiles..................................................................................................

    /**
     * Returns true if this archive contains JRE bootstrap class files, by testing if java.lang.Class class file exists.
     */
    private boolean isJreBootstrapClassFiles() {
        if (null == this.jreBootstrapClassFiles) {
            this.testArchive();
        }

        return this.jreBootstrapClassFiles;
    }

    private Boolean jreBootstrapClassFiles;

    // isJreClassFiles..................................................................................................

    /**
     * Returns true if this archive contains JRE class files, by testing if java.lang.Class class file exists.
     */
    private boolean isJreClassFiles() {
        if (null == this.jreClassFiles) {
            this.testArchive();
        }

        return this.jreClassFiles;
    }

    private Boolean jreClassFiles;

    /**
     * Attempts to open the archive and detect if a file exists otherwise returns false.
     */
    private synchronized void testArchive() {
        final boolean annotationProcessor;
        final boolean javascriptBootstrapFiles;
        final boolean javascriptFiles;
        final boolean jreBootstrapClassFiles;
        final boolean jreClassFiles;

        final J2clPath file = this.artifactFile;
        if (null != file) {
            try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.file().toURI()), Collections.emptyMap())) {
                annotationProcessor = Files.exists(zip.getPath(META_INF_SERVICES_PROCESSOR));

                javascriptBootstrapFiles = Files.exists(zip.getPath(JAVASCRIPT_BOOTSTRAP));
                javascriptFiles = Files.exists(zip.getPath(JAVASCRIPT_FILE));

                jreBootstrapClassFiles = Files.exists(zip.getPath(JAVA_BOOTSTRAP_CLASSFILE));
                jreClassFiles = Files.exists(zip.getPath(JAVA_CLASSFILE));
            } catch (final IOException cause) {
                throw new J2clException("Failed reading archive while trying to test", cause);
            }
        } else {
            annotationProcessor = false;

            javascriptBootstrapFiles = false;
            javascriptFiles = false;

            jreBootstrapClassFiles = false;
            jreClassFiles = false;
        }

        this.annotationProcessor = annotationProcessor;

        this.javascriptBootstrapFiles = javascriptBootstrapFiles;
        this.javascriptFiles = javascriptFiles;

        this.jreBootstrapClassFiles = jreBootstrapClassFiles;
        this.jreClassFiles = jreClassFiles;
    }

    private final static String JAVA_BOOTSTRAP_CLASSFILE = "/java/lang/invoke/MethodType.class";
    private final static String JAVA_CLASSFILE = "/java/lang/Class.class";
    private final static String JAVASCRIPT_BOOTSTRAP = "/closure/goog/base.js";
    private final static String JAVASCRIPT_FILE = "/java/lang/Class.java.js";
    private final static String META_INF_SERVICES_PROCESSOR = "/META-INF/services/"
            .concat(javax.annotation.processing.Processor.class.getName())
            .replace('/', File.separatorChar);

    // isDuplicate......................................................................................................

    /**
     * Checks if this dependency is a duplicate of another, this tries to determine if this is a classifier=source
     * for another binary artifact.
     */
    private boolean isDuplicate() {
        final J2clArtifactCoords coords = this.coords();

        return J2clDependency.COORD_TO_DEPENDENCY.keySet()
                .stream()
                .anyMatch(c -> c.isGroupArtifactSources(coords));
    }

    // shade............................................................................................................

    Map<String, String> shadeMappings() throws IOException {
        if (null == this.shadeMappings) {
            this.shadeMappings = this.loadShadeFile();
        }
        return this.shadeMappings;
    }

    /**
     * The cached shade mappings file as a {@link Map}.
     */
    private Map<String, String> shadeMappings;

    private Map<String, String> loadShadeFile() throws IOException {
        final J2clPath file = this.step(J2clStep.UNPACK)
                .output()
                .shadeFile();
        return file.exists().isPresent() ?
                file.readShadeFile() :
                Maps.empty();
    }

    // tasks............................................................................................................

    J2clRequest request() {
        return this.request;
    }

    private final J2clRequest request;

    // pretty...........................................................................................................

    /**
     * Pretty prints all dependencies with indentation.
     */
    J2clDependency prettyPrintDependencies() {
        this.dependencies();

        final Set<J2clDependency> sorted = Sets.sorted();
        sorted.addAll(COORD_TO_DEPENDENCY.values());

        final J2clLogger logger = this.request.logger();

        final J2clLinePrinter printer = J2clLinePrinter.with(logger.printer(logger::debug));
        printer.printLine("Dependencies graph for all artifacts");
        printer.indent();

        for (J2clDependency artifact : sorted) {
            printer.printLine(artifact.toString());
            printer.indent();
            {
                for (J2clDependency dependency : artifact.dependencies()) {
                    printer.printLine(dependency.toString());
                }
            }
            printer.outdent();
        }
        printer.outdent();
        printer.flush();

        return this;
    }

    // job..............................................................................................................

    /**
     * Returns the {@link Callable job}.
     */
    Callable<J2clDependency> job() {
        return this::job0;
    }

    /**
     * Executes all steps in order for this artifact. This assumes that all dependencies have already completed successfully.
     */
    private J2clDependency job0() throws Exception {
        final J2clLogger logger = this.request().logger();
        logger.info(this.coords() + " begin");

        this.executeStep(J2clStep.FIRST);

        logger.info(this.coords() + " end");

        this.request()
                .taskCompleted(this);
        return this;
    }

    private void executeStep(final J2clStep step) throws Exception {
        final Optional<J2clStep> next = step.execute(this);
        if (next.isPresent()) {
            this.executeStep(next.get());
        }
    }

    // directories......................................................................................................

    /**
     * Sets the directory for this dependency, assumes the hash has been computed
     */
    J2clDependency setDirectory(final String hash) throws IOException {
        final J2clPath create = this.request.base().append(this.coords.directorySafeName() + "-" + hash);
        final J2clPath previous = this.directory.compareAndExchange(null, create);
        if (null != previous) {
            throw new IllegalStateException("Hash already set for this artifact: " + CharSequences.quote(create.toString()));
        }

        create.append(J2clStep.HASH.directoryName()).createIfNecessary();
        return this;
    }

    /**
     * Getter that returns the base directory for this artifact. Within that directory will have the step directories.
     */
    J2clPath directory() {
        final J2clPath directory = this.directory.get();
        if (null == directory) {
            throw new IllegalStateException("Directory under " + this.request().base() + " missing for " + CharSequences.quote(this.coords().toString()));
        }
        return directory;
    }

    private final AtomicReference<J2clPath> directory = new AtomicReference<>();

    /**
     * Returns a compile step directory, creating it if necessary.
     */
    J2clStepDirectory step(final J2clStep step) {
        return J2clStepDirectory.with(Paths.get(this.directory().toString(), step.directoryName()));
    }

    // maven ...........................................................................................................

    /**
     * Returns all source roots including resources which can be directories or archives.
     */
    List<J2clPath> sourcesRoot() {
        final List<J2clPath> sources = this.compileSourceRoots();
        return sources.size() > 0 ?
                sources :
                this.sourcesArchivePath();
    }

    private List<J2clPath> compileSourceRoots() {
        final J2clRequest request = this.request();
        return request
                .sourcesKind()
                .compileSourceRoots(this.mavenProject, request.base());
    }

    private List<J2clPath> sourcesArchivePath() {
        return this.request.mavenMiddleware()
                .mavenFile(this.coords.source().toString())
                .map(Lists::of)
                .orElse(Lists.empty());
    }

    // artifact.........................................................................................................

    private final Artifact artifact;

    /**
     * Returns the archive file attached to this archive.
     */
    Optional<J2clPath> artifactFile() {
        return Optional.ofNullable(this.artifactFile);
    }

    /**
     * Returns the archive file or fails if absent.
     */
    J2clPath artifactFileOrFail() {
        return this.artifactFile().orElseThrow(() -> new IllegalArgumentException("Archive file missing for " + CharSequences.quote(this.coords().toString())));
    }

    private final J2clPath artifactFile;

    private final MavenProject mavenProject;

    // toString.........................................................................................................
    @Override
    public String toString() {
        return this.coords().toString();
    }

    // Comparable.......................................................................................................

    @Override
    public int compareTo(final J2clDependency other) {
        return this.coords.compareTo(other.coords);
    }
}
