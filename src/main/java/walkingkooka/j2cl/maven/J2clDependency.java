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
import walkingkooka.ToStringBuilder;
import walkingkooka.ToStringBuilderOption;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.IOException;
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
                .orElseThrow(() -> new IllegalArgumentException("Unknown coords " + CharSequences.quote(coords.toString())));
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

            if (false == scope.scopeFilter().test(artifact)) {
                continue;
            }

            final J2clArtifactCoords coords = J2clArtifactCoords.with(artifact);
            if(request.isExcluded(coords)) {
                continue;
            }

            this.dependencyCoords.add(this.getOrCreate(coords).coords());
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
        final J2clDependency dependency = COORD_TO_DEPENDENCY.get(this.request().replacement(coords).orElse(coords));

        return null != dependency ?
                dependency :
                this.loadDependency(coords);
    }

    private J2clDependency loadDependency(final J2clArtifactCoords coords) {
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
        return getOrFail(coords).isProcessingRequired();
    }

    /**
     * Returns the classpath and dependencies in order without any duplicates.
     */
    Set<J2clDependency> classpathAndDependencies() {
        return Sets.readOnly(Stream.concat(this.request().classpathRequired().stream(), this.dependencies().stream())
                .filter(this::isDifferent)
                .filter(J2clDependency::isClasspathRequired)
                .collect(Collectors.toCollection(Sets::ordered)));
    }

    private boolean isDifferent(final J2clDependency other) {
        return 0 != this.compareTo(other);
    }

    // isDependency.....................................................................................................

    boolean isDependency() {
        return null != this.artifactFile;
    }

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

    // coords...........................................................................................................

    J2clArtifactCoords coords() {
        return this.coords;
    }

    /**
     * The coords containing the groupid, artifact-id, version and classifier
     */
    private final J2clArtifactCoords coords;

    /**
     * Only returns true for artifacts that are actually the java Bootstrap in some form.
     */
    private boolean isClasspathRequired() {
        final J2clRequest request = this.request();
        final J2clArtifactCoords coords = this.coords();
        return request.isClasspathRequired(coords) || false == request.isJavascriptSourceRequired(coords);
    }

    /**
     * Only returns true for the JRE binary artifact
     */
    boolean isJavascriptSourceRequired() {
        final J2clRequest request = this.request();
        final J2clArtifactCoords coords = this.coords();
        return request.isJavascriptSourceRequired(coords) || false == request.isClasspathRequired(coords);
    }

    /**
     * Used to test if a dependency should be ignored and the archive files used as they are. Examples of this include
     * the prepackaged JRE binaries and the jszip form, each used dependening whether class files or java source is
     * required.
     */
    boolean isProcessingSkipped() {
        return this.request().isProcessingSkipped(this.coords);
    }

    boolean isProcessingRequired() {
        return false == this.isProcessingSkipped();
    }

    // excluded..........................................................................................................

    /**
     * Excluded dependencies will return true, and will be ignored/removed during the dependency discovering phase.
     */
    boolean isExcluded() {
        return this.request().isExcluded(this.coords());
    }

    // tasks............................................................................................................

    J2clRequest request() {
        return this.request;
    }

    private final J2clRequest request;

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
     * Sets the computed hash which will then be used to create or locate the home directory for this artifact.
     */
    J2clDependency setHash(final HashBuilder hash) throws IOException {
        hash.append(this.request.hash());

        final J2clPath create = this.request.base().append(this.coords.directorySafeName() + "-" + hash);
        final J2clPath previous = this.directory.compareAndExchange(null, create);
        if (null != previous) {
            throw new IllegalStateException("HashBuilder already set for this artifact: " + CharSequences.quote(create.toString()));
        }

        create.createIfNecessary();
        Files.createDirectories(Paths.get(create.toString(), J2clStep.HASH.directoryName()));
        return this;
    }

    /**
     * If the directory is present hashes the entire content or the content of the artifact file.
     */
    String hashOrFail() {
        return this.directory().filename();
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
     * Returns all source roots which can be directories or archives.
     */
    List<J2clPath> sourcesRoot() {
        final List<String> sources = this.compileSourceRoots();
        return null != sources && sources.size() > 0 ?
                Collections.unmodifiableList(sources.stream()
                        .map(s -> J2clPath.with(Paths.get(s)))
                        .collect(Collectors.toList())) :
                this.sourcesArchivePath();
    }

    private List<String> compileSourceRoots() {
        return this.request().sourcesKind.compileSourceRoots(this.mavenProject);
    }

    private List<J2clPath> sourcesArchivePath() {
        return this.request.mavenMiddleware()
                .mavenFile(this.coords.source().toString())
                .map(Lists::of)
                .orElse(Lists.empty());
    }

    // artifact.........................................................................................................

    private Artifact artifact() {
        return this.artifact;
    }

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
        final ToStringBuilder b = ToStringBuilder.empty();

        b.value(this.coords());
        b.value(this.artifact().getScope());

        b.disable(ToStringBuilderOption.QUOTE);
        b.valueSeparator(",");

        if (this.isClasspathRequired()) {
            b.value("CLASSPATH-REQUIRED");
        }
        if (this.isJavascriptSourceRequired()) {
            b.value("JAVASCRIPT-SOURCE-REQUIRED");
        }
        if (this.isProcessingSkipped()) {
            b.value("PROCESS-SKIPPED");
        }

        return b.build();
    }

    // Comparable.......................................................................................................

    @Override
    public int compareTo(final J2clDependency other) {
        return this.coords.compareTo(other.coords);
    }
}
