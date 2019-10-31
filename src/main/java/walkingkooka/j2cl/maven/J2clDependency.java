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
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.project.MavenProject;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
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
        return gatherDependencies(project,
                project.getArtifact(),
                false, // dependency=false
                request);
    }

    /**
     * Collects all immediate dependencies for each dependency while walking the entire tree of dependencies.
     */
    private static J2clDependency gatherDependencies(final MavenProject project,
                                                     final Artifact parentArtifact,
                                                     final boolean dependency,
                                                     final J2clBuildRequest request) {
        final J2clArtifactCoords parentCoords = J2clArtifactCoords.with(parentArtifact);
        final J2clDependency parent = new J2clDependency(parentCoords,
                parentArtifact,
                project,
                dependency,
                request);
        final J2clClasspathScope scope = request.scope;

        for (final Artifact dependencyArtifact : project.getArtifacts()) {
            if (isSystem(dependencyArtifact)) {
                continue;
            }

            if (false == scope.scopeFilter().test(dependencyArtifact)) {
                continue;
            }

            final J2clArtifactCoords dependencyCoords = J2clArtifactCoords.with(dependencyArtifact);
            J2clDependency child = COORD_TO_DEPENDENCY.get(dependencyCoords);
            if (null == child) {
                gatherDependencies(request.mavenMiddleware().mavenProject(dependencyArtifact),
                        dependencyArtifact, //
                        true, // dependency=true
                        request);
            }
            parent.dependencyCoords.add(dependencyCoords);
        }

        for (final J2clArtifactCoords added : request.addedDependencies(parentCoords)) {
            if (false == parent.dependencyCoords.contains(added)) {
                J2clDependency child = COORD_TO_DEPENDENCY.get(added);
                if (null == child) {
                    final DefaultArtifact defaultArtifact = new DefaultArtifact(added.groupId(),
                            added.artifactId(),
                            added.baseVersion(),
                            scope.scope,
                            added.type(),
                            added.classifier().orElse(null),
                            parentArtifact.getArtifactHandler());

                    final MavenProject mavenProject = request.mavenMiddleware()
                            .mavenProject(defaultArtifact);
                    gatherDependencies(mavenProject,
                            mavenProject.getArtifact(),
                            true, // dependency=true
                            request);
                }
                parent.dependencyCoords.add(added);
            }
        }

        return parent;
    }

    private static boolean isSystem(final Artifact artifact) {
        return Artifact.SCOPE_SYSTEM.equals(artifact.getScope());
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

    // ctor.............................................................................................................

    /**
     * Private ctor use public static methods.
     */
    private J2clDependency(final J2clArtifactCoords coords,
                           final Artifact artifact,
                           final MavenProject mavenProject,
                           final boolean dependency,
                           final J2clBuildRequest request) {
        this.coords = coords;
        this.artifact = artifact;
        this.mavenProject = mavenProject;
        this.dependency = dependency;
        this.request = request;

        if (null != COORD_TO_DEPENDENCY.put(coords, this)) {
            throw new IllegalArgumentException("Duplicate artifact " + this);
        }
    }

    // dependencies.....................................................................................................

    private final Set<J2clArtifactCoords> dependencyCoords = Sets.sorted();

    /**
     * Returns the immediate dependencies without any transitives.
     */
    Set<J2clDependency> dependencies() {
        if (null == this.dependencies) {
            final J2clBuildRequest request = this.request();
            final Set<J2clDependency> dependencies = this.dependencyCoords
                    .stream()
                    .map(request::dependency)
                    .collect(Collectors.toCollection(Sets::sorted));
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

    public boolean isDependency() {
        return this.dependency;
    }

    private final boolean dependency;

    /**
     * Returns the JAVAC BOOTSTRAP dependency
     */
    static J2clDependency javacBootstrap() {
        if (null == javaBootstrap) {
            javaBootstrap = COORD_TO_DEPENDENCY.values()
                    .stream()
                    .filter(J2clDependency::isJavacBootstrap)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unable to find javac bootstrap dependency: " + COORD_TO_DEPENDENCY.values()));
        }
        return javaBootstrap;
    }

    private static J2clDependency javaBootstrap;

    /**
     * Returns the JRE dependency
     */
    static J2clDependency jre() {
        if (null == jre) {
            jre = COORD_TO_DEPENDENCY.values()
                    .stream()
                    .filter(J2clDependency::isJreBinary)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unable to find JRE dependency: " + COORD_TO_DEPENDENCY.values()));
        }
        return jre;
    }

    private static J2clDependency jre;

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
            throw new IllegalStateException("HashBuilder already set for this artifact: " + create);
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
            throw new IllegalStateException("Directory under " + this.request().base + " missing for " + this);
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
                .map(f -> Lists.of(J2clPath.with(f.toPath())))
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
        return Optional.ofNullable(this.artifact().getFile())
                .filter(File::isFile)
                .map(f -> J2clPath.with(f.toPath()));
    }

    private final MavenProject mavenProject;

    // toString.........................................................................................................
    @Override
    public String toString() {
        return this.coords +
                (Optional.ofNullable(this.artifact().getScope()).map(s -> " " + CharSequences.quoteAndEscape(s)).orElse("")) +
                (this.isJre() ? " (JRE)" : "") +
                (this.isJavacBootstrap() ? " (JAVAC BOOTSTRAP)" : "") +
                (this.isExcluded() ? " (EXCLUDED)" : "");// +
        //(GATHERING ? "" : " " + (this.depth() - 1));
    }

    // Comparable.......................................................................................................

    @Override
    public int compareTo(final J2clDependency other) {
        return this.coords.compareTo(other.coords);
    }
}
