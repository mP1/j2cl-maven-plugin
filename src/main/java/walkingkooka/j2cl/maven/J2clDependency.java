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

import com.google.common.collect.Streams;
import org.apache.bcel.Constants;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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
     * Verifies there are not dependencies that share the same group and artifact id but differ in other components.
     */
    static void verifyWithoutConflictsOrDuplicates() {
        final Set<J2clArtifactCoords> duplicateCoords = Sets.sorted();
        final List<String> duplicatesText = Lists.array();

        for (final J2clArtifactCoords coords : COORD_TO_DEPENDENCY.keySet()) {
            // must have been the duplicate of another coord.
            if (duplicateCoords.contains(coords)) {
                continue;
            }

            final List<J2clArtifactCoords> duplicates = COORD_TO_DEPENDENCY.keySet()
                    .stream()
                    .filter(possible -> coords.isSameGroupArtifactDifferentVersion(possible))
                    .collect(Collectors.toList());

            if (duplicates.size() > 0) {
                duplicateCoords.add(coords);
                duplicateCoords.addAll(duplicates);

                duplicatesText.add(Streams.concat(Stream.of(coords), duplicates.stream())
                        .map(J2clArtifactCoords::toString)
                        .collect(Collectors.joining(", ", coords + ", ", "")));
            }
        }

        if (duplicateCoords.size() > 0) {
            throw new IllegalStateException(duplicateCoords.size() + " duplicate(s)\n" + duplicatesText.stream().collect(Collectors.joining("\n")));
        }
    }

    final static Map<J2clArtifactCoords, J2clDependency> COORD_TO_DEPENDENCY = Maps.sorted();

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
                .coords(coords)
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

        final Set<J2clArtifactCoords> required = COORD_TO_DEPENDENCY.values()
                .stream()
                .map(J2clDependency::coords)
                .map(request::dependencyOrFail)
                .filter(J2clDependency::isAnnotationsBootstrapOrJreFiles)
                .map(J2clDependency::coords)
                .collect(Collectors.toCollection(Sets::sorted));

        final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> coordToDependencies = Maps.sorted();

        for (final Entry<J2clArtifactCoords, J2clDependency> coordAndDependency : COORD_TO_DEPENDENCY.entrySet()) {
            coordToDependencies.put(coordAndDependency.getKey(), coordAndDependency.getValue().dependencyCoords);
        }
        final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> calculated = J2clDependencyGraphCalculator.with(coordToDependencies, required)
                .run();

        for (final Entry<J2clArtifactCoords, J2clDependency> coordAndDependency : COORD_TO_DEPENDENCY.entrySet()) {
            final J2clArtifactCoords coords = coordAndDependency.getKey();
            final J2clDependency dependency = coordAndDependency.getValue();

            dependency.dependencies = calculated.get(coords)
                    .stream()
                    .map(request::dependencyOrFail)
                    .collect(Collectors.toCollection(Sets::sorted));
        }
    }

    /**
     * Returns the classpath and dependencies in order without any duplicates.
     */
    Set<J2clDependency> classpathAndDependencies() {
        return Sets.readOnly(Stream.concat(this.discoveredBootstrapAndJre(), Stream.of(this))
                .flatMap(d -> Stream.concat(Stream.of(d), d.dependencies().stream()))
                .filter(this::isDifferent)
                .filter(J2clDependency::isClasspathRequired)
                .collect(Collectors.toCollection(Sets::ordered)));
    }

    private Stream<J2clDependency> discoveredBootstrapAndJre() {
        return COORD_TO_DEPENDENCY.values()
                .stream()
                .filter(J2clDependency::isAnnotationsBootstrapOrJreClassFiles)
                .flatMap(d -> Stream.concat(Stream.of(d), d.dependencies().stream()));
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
            this.testArchive();

            final J2clRequest request = this.request();
            final J2clArtifactCoords coords = this.coords();

            this.classpathRequired = (this.classpathRequiredFile || request.isClasspathRequired(coords) || false == (this.javascriptSourceRequiredFile || request.isJavascriptSourceRequired(coords))) ||
                    this.isAnnotationClassFiles() ||
                    this.isAnnotationProcessor() ||
                    this.isJreBootstrapClassFiles() ||
                    this.isJreClassFiles();
        }
        return this.classpathRequired;
    }

    private Boolean classpathRequired;
    private boolean classpathRequiredFile;

    /**
     * Returns true for artifacts that only contain javascript.
     */
    boolean isJavascriptSourceRequired() {
        if (null == this.javascriptSourceRequired) {
            this.testArchive();

            final J2clRequest request = this.request();
            final J2clArtifactCoords coords = this.coords();

            this.javascriptSourceRequired = (this.javascriptSourceRequiredFile || request.isJavascriptSourceRequired(coords) || false == (this.classpathRequiredFile || request.isClasspathRequired(coords))) &&
                    false == this.isAnnotationClassFiles() &&
                    false == this.isAnnotationProcessor() &&
                    this.isJavascriptBootstrapFiles() ||
                    this.isJavascriptFiles() ||
                    false == this.isJreBootstrapClassFiles() &&
                            false == this.isJreClassFiles();
        }

        return this.javascriptSourceRequired;
    }

    private Boolean javascriptSourceRequired;
    private boolean javascriptSourceRequiredFile;

    /**
     * Used to test if a dependency should be ignored and the archive files used as they are, such as class files
     * which should appear on the classpath, or javascript source that should appear in javascript processing.
     */
    boolean isIgnored() {
        if (null == this.ignored) {
            this.testArchive();

            this.ignored = this.ignoredFile || this.request().isIgnored(this.coords) ||
                    this.isAnnotationClassFiles() ||
                    this.isAnnotationProcessor() ||
                    this.isJavascriptBootstrapFiles() ||
                    this.isJavascriptFiles() ||
                    this.isJreBootstrapClassFiles() ||
                    this.isJreClassFiles();
        }
        return this.ignored;
    }

    private Boolean ignored;
    private Boolean ignoredFile;

    // isAnnotationClassFiles...........................................................................................

    /**
     * Returns true if this dependency only includes annotation class files.
     */
    boolean isAnnotationClassFiles() {
        if (null == this.annotationClassFiles) {
            this.testArchive();
        }

        return this.annotationClassFiles;
    }

    private Boolean annotationClassFiles;

    /**
     * Returns true if this dependency is only annotations, a JRE bootstrap or JRE class files.
     */
    private boolean isAnnotationsBootstrapOrJreClassFiles() {
        return this.isAnnotationClassFiles() ||
                this.isJreBootstrapClassFiles() ||
                this.isJreClassFiles();
    }

    /**
     * Returns true if this dependency is only annotations, a JRE bootstrap or JRE class and javascript files.
     */
    private boolean isAnnotationsBootstrapOrJreFiles() {
        return this.isAnnotationClassFiles() ||
                this.isBootstrapOrJreFiles();
    }

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
    private boolean isBootstrapOrJreFiles() {
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
    boolean isJreBootstrapClassFiles() {
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
    boolean isJreClassFiles() {
        if (null == this.jreClassFiles) {
            this.testArchive();
        }

        return this.jreClassFiles;
    }

    private Boolean jreClassFiles;

    /**
     * Attempts to open the archive and detect if a file exists or contains only annotation class files setting various flags.
     */
    private synchronized void testArchive() {
        final boolean annotationClassFiles;
        final boolean annotationProcessor;
        final boolean classpathRequiredFile;
        final boolean ignoredFile;
        final boolean javascriptBootstrapFiles;
        final boolean javascriptFiles;
        final boolean javascriptSourceRequiredFile;
        final boolean jreBootstrapClassFiles;
        final boolean jreClassFiles;

        final J2clPath file = this.artifactFile;
        if (null != file) {
            try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.file().toURI()), Collections.emptyMap())) {
                annotationProcessor = Files.exists(zip.getPath(META_INF_SERVICES_PROCESSOR));

                classpathRequiredFile = Files.exists(zip.getPath(CLASSFILE_REQUIRED));

                ignoredFile = Files.exists(zip.getPath(IGNORED_DEPENDENCY));

                javascriptBootstrapFiles = Files.exists(zip.getPath(JAVASCRIPT_BOOTSTRAP));
                javascriptFiles = Files.exists(zip.getPath(JAVASCRIPT_FILE));

                javascriptSourceRequiredFile = Files.exists(zip.getPath(JAVASCRIPT_SOURCE_REQUIRED));

                jreBootstrapClassFiles = Files.exists(zip.getPath(JAVA_BOOTSTRAP_CLASSFILE));
                jreClassFiles = Files.exists(zip.getPath(JAVA_CLASSFILE));

                if (false == (annotationProcessor || javascriptBootstrapFiles || javascriptFiles || jreBootstrapClassFiles || jreClassFiles)) {
                    final boolean[] annotations = new boolean[1];

                    Files.walkFileTree(zip.getPath("/"),
                            new SimpleFileVisitor<>() {

                                @Override
                                public FileVisitResult visitFile(final Path file,
                                                                 final BasicFileAttributes attrs) throws IOException {
                                    final FileVisitResult result;

                                    final String path = file.toString();
                                    if (path.startsWith(META_INF) || false == path.endsWith(".class")) {
                                        result = FileVisitResult.CONTINUE;
                                    } else {
                                        final int annotationOrInterface = isAnnotationOrInterface(Files.readAllBytes(file));

                                        switch (annotationOrInterface) {
                                            case 0: // class file
                                                annotations[0] = false;
                                                result = FileVisitResult.TERMINATE;
                                                break;
                                            case 1: // interface ignore
                                                result = FileVisitResult.CONTINUE;
                                                break;
                                            case 2: // annotation
                                                annotations[0] = true;
                                                result = FileVisitResult.CONTINUE;
                                                break;
                                            default:
                                                result = null;
                                                break;
                                        }
                                    }

                                    return result;
                                }
                            });
                    annotationClassFiles = annotations[0];
                } else {
                    annotationClassFiles = false;
                }
            } catch (final IOException cause) {
                throw new J2clException("Failed reading archive while trying to test", cause);
            }
        } else {
            annotationClassFiles = false;
            annotationProcessor = false;

            classpathRequiredFile = false;

            ignoredFile = false;

            javascriptBootstrapFiles = false;
            javascriptFiles = false;

            javascriptSourceRequiredFile = false;

            jreBootstrapClassFiles = false;
            jreClassFiles = false;
        }

        this.annotationClassFiles = annotationClassFiles;
        this.annotationProcessor = annotationProcessor;

        this.classpathRequiredFile = classpathRequiredFile;

        this.ignoredFile = ignoredFile;

        this.javascriptBootstrapFiles = javascriptBootstrapFiles;
        this.javascriptFiles = javascriptFiles;

        this.javascriptSourceRequiredFile = javascriptSourceRequiredFile;

        this.jreBootstrapClassFiles = jreBootstrapClassFiles;
        this.jreClassFiles = jreClassFiles;
    }

    private final static String CLASSFILE_REQUIRED = "/" + J2clPath.FILE_PREFIX + "-classpath-required.txt";
    private final static String IGNORED_DEPENDENCY = "/" + J2clPath.FILE_PREFIX + "-ignored-dependency.txt";
    private final static String JAVA_BOOTSTRAP_CLASSFILE = "/java/lang/invoke/MethodType.class";
    private final static String JAVA_CLASSFILE = "/java/lang/Class.class";
    private final static String JAVASCRIPT_BOOTSTRAP = "/closure/goog/base.js";
    private final static String JAVASCRIPT_FILE = "/java/lang/Class.java.js";
    private final static String JAVASCRIPT_SOURCE_REQUIRED = "/" + J2clPath.FILE_PREFIX + "-javascript-source-required.txt";
    private final static String META_INF = "/META-INF/";
    private final static String META_INF_SERVICES_PROCESSOR = META_INF + "services/" + javax.annotation.processing.Processor.class.getName();

    /**
     * Returns true if the class file is an annotation type by checking that it implements Annotation.
     */
    static int isAnnotationOrInterface(final byte[] content) {
        final int[] result = new int[1];

        new ClassReader(content).accept(new ClassVisitor(Opcodes.ASM7) {

            @Override
            public void visit(final int version,
                              final int access,
                              final String name,
                              final String signature,
                              final String superName,
                              final String[] interfaces) {
                if ((access & Constants.ACC_INTERFACE) > 0) {
                    result[0] =1;
                }

                // annotations implement java.lang.annotation.Annotation
                if (null != interfaces && Lists.of(interfaces).contains(ANNOTATION_TYPE)) {
                    result[0] = 2;
                }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        return result[0];
    }

    private final static String ANNOTATION_TYPE = Annotation.class.getName()
        .replace('.', '/');


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

    // print............................................................................................................

    /**
     * Prints a dependency graph and various metadata that will be used to plan the approach for building.
     */
    void print() {
        final J2clLogger logger = request.logger();
        final J2clLinePrinter printer = J2clLinePrinter.with(logger.printer(logger::debug));
        printer.printLine("Dependencies");
        printer.indent();
        {
            this.prettyPrintDependencies(printer);
            this.printPlanMetadata(printer);
        }
        printer.outdent();
    }

    /**
     * Pretty prints all dependencies with indentation.
     */
    private void prettyPrintDependencies(final J2clLinePrinter printer) {
        this.dependencies();

        final Set<J2clDependency> sorted = Sets.sorted();
        sorted.addAll(COORD_TO_DEPENDENCY.values());

        printer.printLine("Graph");
        printer.indent();
        {

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
        }
        printer.outdent();
        printer.flush();
    }

    /**
     * Prints all the plan metadata such as:
     * <ul>
     *  <li>classpath required dependencies</li>
     *  <li>ignoredDependencies dependencies</li>
     *  <li>javascript source required dependencies</li>
     * </ul>
     */
    private void printPlanMetadata(final J2clLinePrinter printer) {
        printer.printLine("Metadata");
        printer.indent();
        {
            this.print("Annotation processor", J2clDependency::isAnnotationProcessor, printer);
            this.print("Annotation only class files", J2clDependency::isAnnotationClassFiles, printer);
            this.print("Classpath required", J2clDependency::isClasspathRequired, printer);
            this.print("JRE bootstrap class files", J2clDependency::isJreBootstrapClassFiles, printer);
            this.print("JRE class files", J2clDependency::isJreClassFiles, printer);
            this.print("Ignored dependencies", J2clDependency::isIgnored, printer);
            this.print("Javascript source required", J2clDependency::isJavascriptSourceRequired, printer);
            this.print("Javascript bootstrap class files", J2clDependency::isJavascriptBootstrapFiles, printer);
            this.print("Javascript class files", J2clDependency::isJavascriptFiles, printer);
        }
        printer.outdent();
        printer.flush();
    }

    private void print(final String label,
                       final Predicate<J2clDependency> filter,
                       final J2clLinePrinter printer) {

        printer.printIndentedString(label,
                J2clDependency.COORD_TO_DEPENDENCY.values()
                        .stream()
                        .filter(filter)
                        .map(J2clDependency::toString)
                        .collect(Collectors.toList()));
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
