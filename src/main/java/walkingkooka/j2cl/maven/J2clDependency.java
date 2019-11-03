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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a single artifact including the project or any of its dependencies. These will in turn have java files etc.
 */
final class J2clDependency implements Comparable<J2clDependency> {

    /**
     * Retrieves all {@link J2clDependency} in order of leaf to the project itself which should be last.
     */
    static J2clDependency gather(final MavenProject project,
                                 final J2clBuildRequest request) {
        return new J2clDependency(J2clArtifactCoords.with(project.getArtifact()),
                project,
                null,
                request);
    }

    /**
     * Lookup the coords returning the dependency
     */
    static J2clDependency getOrFail(final J2clArtifactCoords coords) {
        final J2clDependency dependency = COORD_TO_DEPENDENCY.get(coords);
        if (null == dependency) {
            throw new IllegalArgumentException("Unknown coords " + coords);
        }
        return dependency;
    }

    /**
     * Tests if the given coords are also declared as an artifact.
     */
    static boolean isArtifactDeclared(final J2clArtifactCoords coords) {
        return COORD_TO_DEPENDENCY.containsKey(coords);
    }

    private final static Map<J2clArtifactCoords, J2clDependency> COORD_TO_DEPENDENCY = Maps.sorted();

    /**
     * Switch that becomes true when gathering the JRE or any of its dependencies. When true dependencies are
     * added to {@link #JRE_OR_JRE_DEPENDENCIES}
     */
    private static boolean GATHERING_JRE_DEPENDENCIES = false;

    /**
     * Holds the coords of any dependencies that are JRE dependencies.
     */
    private final static Set<J2clArtifactCoords> JRE_OR_JRE_DEPENDENCIES = Sets.sorted();

    // ctor.............................................................................................................

    /**
     * Private ctor use public static methods.
     */
    private J2clDependency(final J2clArtifactCoords coords,
                           final MavenProject mavenProject,
                           final J2clPath artifactFile,
                           final J2clBuildRequest request) {
        final Artifact artifact = mavenProject.getArtifact();
        this.coords = coords;
        this.artifact = artifact;
        this.mavenProject = mavenProject;
        this.artifactFile = artifactFile;
        this.request = request;

        if (null != COORD_TO_DEPENDENCY.put(coords, this)) {
            throw new IllegalArgumentException("Duplicate artifact " + CharSequences.quote(coords.toString()));
        }

        final boolean jre = this.isJre();
        if (jre) {
            GATHERING_JRE_DEPENDENCIES = true;
            JRE_OR_JRE_DEPENDENCIES.add(coords);
        }
        this.gatherDeclaredDependencies();
        this.addAddedDependencies();
        GATHERING_JRE_DEPENDENCIES = false;
    }

    private void gatherDeclaredDependencies() {
        final J2clBuildRequest request = this.request();
        final J2clClasspathScope scope = request.scope;

        for (final Artifact artifact : this.mavenProject.getArtifacts()) {
            if (isSystemScope(artifact)) {
                continue;
            }

            if (false == scope.scopeFilter().test(artifact)) {
                continue;
            }

            this.dependencyCoords.add(this.getOrCreate(J2clArtifactCoords.with(artifact)).coords());
        }
    }

    private static boolean isSystemScope(final Artifact artifact) {
        return Artifact.SCOPE_SYSTEM.equals(artifact.getScope());
    }

    private void addAddedDependencies() {
        final J2clBuildRequest request = this.request();

        for (final J2clArtifactCoords added : request.addedDependencies(this.coords())) {
            if (false == this.dependencyCoords.contains(added)) {
                this.getOrCreate(added);
                this.dependencyCoords.add(added);
            }
        }
    }

    private J2clDependency getOrCreate(final J2clArtifactCoords coords) {
        if (GATHERING_JRE_DEPENDENCIES) {
            JRE_OR_JRE_DEPENDENCIES.add(coords);
        }
        final J2clDependency dependency = COORD_TO_DEPENDENCY.get(coords);
        return null != dependency ?
                dependency :
                this.loadDependency(coords);
    }

    private J2clDependency loadDependency(final J2clArtifactCoords coords) {
        final J2clBuildRequest request = this.request();
        final MavenProject project = request.mavenMiddleware()
                .mavenProject(coords.mavenArtifact(request.scope, this.artifact.getArtifactHandler()));

        return new J2clDependency(coords,
                project,
                request.mavenMiddleware().mavenFile(coords.toString()).orElseThrow(() -> new IllegalArgumentException("Archive file missing archive for " + CharSequences.quote(coords.toString())))/*.map(J2clPath.with())*/,
                request);
    }

    // dependencies.....................................................................................................

    private final Set<J2clArtifactCoords> dependencyCoords = Sets.sorted();

    /**
     * Returns the immediate dependencies without any transitives. If this is a non JRE dependency, and has no dependencies
     * the JRE will be added as a dependency.
     */
    Set<J2clDependency> dependencies() {
        if (null == this.dependencies) {
            final Set<J2clArtifactCoords> dependencyCoords = this.dependencyCoords;
            final Set<J2clDependency> dependencies;

            if (dependencyCoords.isEmpty()) {
                dependencies = JRE_OR_JRE_DEPENDENCIES.contains(this.coords()) ?
                        Sets.empty() :
                        Sets.of(jre());
            } else {
                final J2clBuildRequest request = this.request();
                dependencies = this.dependencyCoords
                        .stream()
                        .map(request::dependency)
                        .collect(Collectors.toCollection(Sets::sorted));
            }
            this.dependencies = Sets.readOnly(dependencies);
        }

        return this.dependencies;
    }

    private Set<J2clDependency> dependencies;

    /**
     * Retrieves all dependencies including transients.
     */
    Set<J2clDependency> dependenciesIncludingTransitives() {
        if (null == this.dependenciesIncludingTransitives) {

            final Set<J2clDependency> all = Sets.sorted();
            for (final J2clDependency dependency : this.dependencies()) {
                all.add(dependency);
                all.addAll(dependency.dependencies());
            }
            this.dependenciesIncludingTransitives = Collections.unmodifiableSet(all);
        }

        return this.dependenciesIncludingTransitives;
    }

    private Set<J2clDependency> dependenciesIncludingTransitives;

    // isDependency.....................................................................................................

    boolean isDependency() {
        return null != this.artifactFile;
    }


    /**
     * Returns the JAVAC BOOTSTRAP dependency
     */
    static J2clDependency javacBootstrap() {
        if (null == javaBootstrap) {
            javaBootstrap = findDependency(J2clDependency::isJavacBootstrap, "Java bootstrap");
        }
        return javaBootstrap;
    }

    private static J2clDependency javaBootstrap;

    /**
     * Returns the JRE dependency
     */
    static J2clDependency jre() {
        if (null == jre) {
            jre = findDependency(J2clDependency::isJreBinary, "JRE");
        }
        return jre;
    }

    private static J2clDependency jre;

    private static J2clDependency findDependency(final Predicate<J2clDependency> filter,
                                                 final String label) {
        return COORD_TO_DEPENDENCY.values()
                .stream()
                .filter(filter)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to find " + label + " dependency: " + COORD_TO_DEPENDENCY.values().stream().map(d -> CharSequences.quote(d.coords().toString())).collect(Collectors.joining(","))));
    }

    // pretty...........................................................................................................

    /**
     * Pretty prints all dependencies with indentation.
     */
    J2clDependency prettyPrintDependencies() {
        final J2clLogger logger = this.request.logger();

        final J2clLinePrinter printer = J2clLinePrinter.with(logger.printer(logger::debug));
        printer.printLine(this.coords.toString());
        printer.indent();

        printer.printLine("Dependencies graph");

        printer.indent();
        this.prettyPrintDependencies0(printer);
        printer.outdent();

        printer.printLine(COORD_TO_DEPENDENCY.values().stream().filter(J2clDependency::isIncluded).count() + " unique artifacts");
        printer.outdent();
        printer.flush();

        return this;
    }

    private void prettyPrintDependencies0(final J2clLinePrinter printer) {
        final J2clDependency replacement = this.request().dependency(this.coords());
        if (this.equals(replacement)) {
            printer.printLine(this.toString());
        } else {
            printer.printLine(this.toString() + " -> " + replacement.toString());
        }

        printer.indent();
        replacement.dependencies().forEach(d -> d.prettyPrintDependencies0(printer));
        printer.outdent();
    }

    // coords...........................................................................................................

    J2clArtifactCoords coords() {
        return this.coords;
    }

    /**
     * The coords containing the groupid, artifact-id, version and classifier
     */
    private final J2clArtifactCoords coords;

    // bootstrap........................................................................................................

    /**
     * Only returns true for artifacts that are actually the Bootstrap binaries in some form.
     */
    boolean isJavacBootstrap() {
        return this.request().isJavacBootstrap(this.coords);
    }

    // jre..............................................................................................................

    boolean isJre() {
        return this.request().isJre(this.coords);
    }

    /**
     * Only returns true for artifacts that are actually the JRE binaries in some form, but not the jszip form.
     */
    boolean isJreBinary() {
        return this.isJre() && false == JSZIP.equals(this.coords().classifier());
    }

    private final static Optional<String> JSZIP = Optional.of("jszip");

    // excluded..........................................................................................................

    /**
     * Excluded dependencies will return true. These must be excluded during build classpaths etc.
     */
    boolean isExcluded() {
        return this.request().isExcluded(this.coords());
    }

    boolean isIncluded() {
        return false == this.isExcluded();
    }

    // tasks............................................................................................................

    J2clBuildRequest request() {
        return this.request;
    }

    private final J2clBuildRequest request;

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

        this.executeStep(J2clBuildStep.FIRST);

        logger.info(this.coords() + " end");

        this.request()
                .taskCompleted(this);
        return this;
    }

    private void executeStep(final J2clBuildStep step) throws Exception {
        final Optional<J2clBuildStep> next = step.execute(this);
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

        final J2clPath create = J2clPath.with(Paths.get(this.request.base.toString(), this.coords.directorySafeName() + "-" + hash));
        final J2clPath previous = this.directory.compareAndExchange(null, create);
        if (null != previous) {
            throw new IllegalStateException("HashBuilder already set for this artifact: " + CharSequences.quote(create.toString()));
        }

        create.createIfNecessary();
        Files.createDirectories(Paths.get(create.toString(), J2clBuildStep.HASH.directoryName()));
        return this;
    }

    /**
     * Returns the hash for this artifact or fails if the hash has not yet been computed.
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
            throw new IllegalStateException("Directory under " + this.request().base + " missing for " + CharSequences.quote(this.coords().toString()));
        }
        return directory;
    }

    private final AtomicReference<J2clPath> directory = new AtomicReference<>();

    /**
     * Returns a compile step directory, creating it if necessary.
     */
    J2clStepDirectory step(final J2clBuildStep step) {
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
                .map(f -> Lists.of(f))
                .orElse(Lists.empty());
    }

    // artifact.........................................................................................................

    private Artifact artifact() {
        return this.artifact;
    }

    private final Artifact artifact;

    /**
     * Returns the archive file attached to this archive, and never any target/classes directory.
     */
    Optional<J2clPath> artifactFile() {
        return Optional.ofNullable(this.artifactFile);
    }

    private final J2clPath artifactFile;

    private final MavenProject mavenProject;

    // toString.........................................................................................................
    @Override
    public String toString() {
        return this.coords +
                (Optional.ofNullable(this.artifact().getScope()).map(s -> " " + CharSequences.quoteAndEscape(s)).orElse("")) +
                (this.isJre() ? " (JRE)" : "") +
                (this.isJavacBootstrap() ? " (JAVAC BOOTSTRAP)" : "") +
                (this.isExcluded() ? " (EXCLUDED)" : "");// +
    }

    // Comparable.......................................................................................................

    @Override
    public int compareTo(final J2clDependency other) {
        return this.coords.compareTo(other.coords);
    }
}
