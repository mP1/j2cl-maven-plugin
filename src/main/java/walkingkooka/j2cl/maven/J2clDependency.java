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
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.predicate.Predicates;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a single artifact including the project or any of its dependencies. These will in turn have java files etc.
 */
public final class J2clDependency implements Comparable<J2clDependency> {

    static Set<J2clDependency> set() {
        return Sets.ordered();
    }

    /**
     * Gathers all dependencies honouring excludes and dependencyManagement entries in POMs.
     */
    static J2clDependency gather(final MavenProject project,
                                 final J2clMavenContext context) {
        final TreeLogger logger = context.mavenLogger()
                .output();
        final J2clDependency root;
        {
            root = timeTask(
                    "Gather dependencies",
                    () -> {
                        final J2clDependency r = new J2clDependency(J2clArtifactCoords.with(project.getArtifact()),
                                project,
                                Optional.empty(),
                                context);
                        r.gatherDependencies(context.scope(), Predicates.never(), Function.identity());
                        return r;
                    },
                    logger
            );

            timeTask(
                    "Reduce duplicate dependencies",
                    () -> {
                        root.reduce();
                        return null;
                    },
                    logger
            );

            timeTask(
                    "Discover and mark ignored transitive dependencies",
                    () -> {
                        root.discoverAndMarkIgnoredTransitiveDependencies();
                        return null;
                    },
                    logger
            );

            timeTask(
                    "Expand dependencies",
                    () -> {
                        root.addTransitiveDependencies();
                        return null;
                    },
                    logger
            );

            timeTask(
                    "Add Bootstrap Classpath to all dependencies",
                    () -> {
                        root.addBootstrapClasspath();
                        return null;
                    },
                    logger
            );

            timeTask(
                    "Verify dependencies maven coordinates",
                    () -> {
                        root.verify();
                        return null;
                    },
                    logger
            );

            makeDependenciesGetterReadOnly(root.dependencies);
        }
        root.log(true);

        return root;
    }

    private static <T> T timeTask(final String taskName,
                                  final Supplier<T> run,
                                  final TreeLogger logger) {
        final T result;

        final Thread thread = Thread.currentThread();
        final String threadNameBackup = thread.getName();
        thread.setName(taskName);
        try {
            logger.line(taskName);
            logger.indent();
            {
                final Instant start = Instant.now();
                logger.indent();
                {
                    result = run.get();
                }
                logger.outdent();
                logger.line(
                        taskName +
                                " took " +
                                TreeLogger.prettyTimeTaken(
                                        Duration.between(start, Instant.now())
                                )
                );
            }
            logger.outdent();
        } finally {
            thread.setName(threadNameBackup);
        }
        return result;
    }

    // ctor.............................................................................................................

    private J2clDependency(final J2clArtifactCoords coords,
                           final MavenProject project,
                           final Optional<J2clPath> artifactFile,
                           final J2clMavenContext context) {
        super();
        this.coords = coords;
        this.project = project;
        this.artifactFile = artifactFile;
        this.context = context;
    }

    private void gatherDependencies(final J2clClasspathScope scope,
                                    final Predicate<J2clArtifactCoords> parentExclusions,
                                    final Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement) {
        this.gatherDependencies0(scope, parentExclusions, this.dependencyManagement(dependencyManagement));
    }

    private Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement(final Function<J2clArtifactCoords, J2clArtifactCoords> transformer) {
        final DependencyManagement management = this.project().getDependencyManagement();
        return null == management ?
                transformer :
                this.dependencyManagement0(transformer, management);
    }

    private Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement0(final Function<J2clArtifactCoords, J2clArtifactCoords> transformer,
                                                                                   final DependencyManagement dependencyManagement) {
        final List<Dependency> dependencies = dependencyManagement.getDependencies();
        return null == dependencies ?
                transformer :
                this.dependencyManagement1(transformer, dependencies);
    }

    private Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement1(final Function<J2clArtifactCoords, J2clArtifactCoords> transformer,
                                                                                   final List<Dependency> dependencies) {
        Function<J2clArtifactCoords, J2clArtifactCoords> result = transformer;
        for (final Dependency dependency : dependencies) {
            result = result.andThen(J2clArtifactCoords.with(dependency).dependencyManagementTransformer());
        }

        return result;
    }

    private void gatherDependencies0(final J2clClasspathScope scope,
                                     final Predicate<J2clArtifactCoords> parentExclusions,
                                     final Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement) {
        final J2clMavenContext context = this.context;

        final Predicate<String> scopeFilter = scope.scopeFilter();
        for (final Dependency dependency : this.project().getDependencies()) {
            // filter if wrong scope
            if (false == scopeFilter.test(dependency.getScope())) {
                continue;
            }

            // filter if excluded by ancestor.excludes
            final J2clArtifactCoords coords = J2clArtifactCoords.with(dependency);
            if (coords.isSources()) {
                continue;
            }

            if (parentExclusions.test(coords)) {
                continue;
            }

            // transform the coords to the corrected version if any dependencyManagement entries exist.
            final J2clDependency child = new J2clDependency(dependencyManagement.apply(coords),
                    null,
                    null,
                    context);
            this.dependencies.add(child);

            child.gatherDependencies(J2clClasspathScope.COMPILE,
                    exclusions(parentExclusions, dependency),
                    dependencyManagement);
        }
    }

    /**
     * Factory that returns a new {@link Predicate} combining the parent predicate with zero or more created from the
     * given {@link Dependency#getExclusions()}.
     */
    private static Predicate<J2clArtifactCoords> exclusions(final Predicate<J2clArtifactCoords> parentExclusions,
                                                            final Dependency dependency) {
        final List<Exclusion> exclusions = dependency.getExclusions();
        return null == exclusions || exclusions.isEmpty() ?
                parentExclusions :
                exclusions0(parentExclusions, exclusions);
    }

    private static Predicate<J2clArtifactCoords> exclusions0(final Predicate<J2clArtifactCoords> parent,
                                                             final List<Exclusion> exclusions) {
        Predicate<J2clArtifactCoords> child = parent;

        for (final Exclusion exclusion : exclusions) {
            child = child.or(J2clArtifactCoordsExclusionPredicate.with(exclusion.getGroupId(), exclusion.getArtifactId()));
        }

        return child;
    }

    // reduce...........................................................................................................

    /**
     * This is required because a dependency may appear in multiple places within a graph of dependencies from the project
     * being built. Because of this the same artifact coordinates may have different dependencies due to exclusions. The
     * one with less will be kept.
     */
    private J2clDependency reduce() {
        final Map<J2clArtifactCoords, J2clDependency> coordToDependency = Maps.sorted(J2clArtifactCoords.IGNORE_VERSION_COMPARATOR);
        this.gatherCoordToDependency(coordToDependency);
        return this.reduce0(coordToDependency);
    }

    private void gatherCoordToDependency(final Map<J2clArtifactCoords, J2clDependency> coordToDependency) {
        final J2clArtifactCoords coords = this.coords();

        final J2clDependency other = coordToDependency.get(coords);

        // different dependency instance might have different child dependencies due to "different" exclusions.
        if(false == this.equals(other)){
            final Set<J2clDependency> dependencies = this.dependencies;
            if(null == other || dependencies.size() < other.dependencies.size()) {
                coordToDependency.put(coords, this);
            }
            for (final J2clDependency dependency : dependencies) {
                dependency.gatherCoordToDependency(coordToDependency);
            }
        }
    }

    private J2clDependency reduce0(final Map<J2clArtifactCoords, J2clDependency> coordToDependency) {
        this.dependencies = this.dependencies.stream()
                .map(d -> coordToDependency.get(d.coords()).reduce0(coordToDependency))
                .collect(Collectors.toCollection(J2clDependency::set));
        return this;
    }

    // discoverAndMarkIgnoredTransitiveDependencies....................................................................

    /**
     * Builds a graph of dependency to parents recursively until the project being built. All artifacts parent graphs are
     * then walked until a non ignored ancestor or the project itself is found, otherwise that is marked as ignored.
     * <p>
     * In the graph below transitive (both paths) and transitive2 will be found to only have ancestors that are ignored
     * and therefore will also be marked as ignored.
     * <pre>
     * transitive
     *   -> dependency (ignored)
     *     -> project
     *   -> transitive2
     *     -> dependency (ignored)
     *       -> project
     * </pre>
     */
    private void discoverAndMarkIgnoredTransitiveDependencies() {
        final Map<J2clDependency, Set<J2clDependency>> childToParents = Maps.sorted();

        this.discoverAndRecordParents(childToParents);
        this.markIgnoredTransitiveDependencies(childToParents);
    }

    /**
     * Builds a {@link Map} of child to parent.
     */
    private void discoverAndRecordParents(final Map<J2clDependency, Set<J2clDependency>> childToParents) {
        this.dependencies.stream()
                .filter(c -> false == c.isAnnotationsBootstrapOrJreFiles())
                .forEach(c -> {
                    Set<J2clDependency> parents = childToParents.get(c);
                    if (null == parents) {
                        parents = set();
                        childToParents.put(c, parents);
                    }
                    parents.add(this);

                    c.discoverAndRecordParents(childToParents);
                });
    }

    private void markIgnoredTransitiveDependencies(final Map<J2clDependency, Set<J2clDependency>> childToParents) {
        final TreeLogger logger = this.context.mavenLogger()
                .output();

        logger.line("Marking ignored transitive dependencies");
        logger.indent();
        {
            childToParents.entrySet()
                    .forEach(e -> {
                        final Set<J2clDependency> parents = e.getValue();
                        if (null != parents) {
                            e.getKey()
                                    .markIgnoredTransitiveDependencies0(
                                            parents,
                                            childToParents,
                                            logger
                                    );
                        }
                    });
        }
        logger.outdent();
    }

    /**
     * <pre>
     * child >
     *      parent1 >
     *              grand1 >
     *      parent2 >
     *              grand2 >
     *                       root
     * </pre>
     * // stop walking if parent is ignored
     */
    private void markIgnoredTransitiveDependencies0(final Set<J2clDependency> parents,
                                                    final Map<J2clDependency, Set<J2clDependency>> childToParents,
                                                    final TreeLogger logger) {
        final boolean nonIgnored = this.findNonIgnoredParentDependencies(parents, childToParents);
        if (false == nonIgnored) {
            this.ignored = true;

            this.logParentDependencies(
                    childToParents,
                    logger
            );
        }
    }

    private boolean findNonIgnoredParentDependencies(final Set<J2clDependency> parents,
                                                     final Map<J2clDependency, Set<J2clDependency>> childToParents) {
        boolean nonIgnored = false;

        if (false == this.isIgnored()) {
            for (final J2clDependency parent : parents) {
                final Set<J2clDependency> childParents = childToParents.get(parent);
                if (null == childParents) {
                    nonIgnored = true;
                    break;
                }
                if (parent.findNonIgnoredParentDependencies(childParents, childToParents)) {
                    nonIgnored = true;
                    break;
                }
            }
        }

        return nonIgnored;
    }

    /**
     * Used to print a tree of ignored dependency to all its parents, which may be useful in debugging transpile failures
     * due to ignored dependencies.
     */
    private void logParentDependencies(final Map<J2clDependency, Set<J2clDependency>> childToParents,
                                       final TreeLogger logger) {
        final Set<J2clDependency> parents = childToParents.get(this);
        if (null != parents) {
            logger.indent();
            {
                parents.forEach(
                        p -> {
                            if (p.isIgnored()) {
                                logger.line(p.coords().toString());
                                p.logParentDependencies(childToParents, logger);
                            }
                        }
                );
            }
            logger.outdent();
        }
    }

    // expand dependencies..............................................................................................

    /**
     * Before this is called, all {@link J2clDependency#dependencies} only contain their children, when this method
     * finishes it will all descendants
     */
    private Set<J2clDependency> addTransitiveDependencies() {
        final Set<J2clDependency> deep = set();

        for (final J2clDependency child : this.dependencies()) {
            if (false == deep.contains(child)) {
                deep.add(child);
                child.addTransitiveDependencies()
                        .stream()
                        .filter(d -> false == deep.contains(d))
                        .forEach(deep::add);
            }
        }

        this.dependencies = deep;
        return deep;
    }

    // addBootstrapClasspath............................................................................................

    /**
     * Add the bootstrap and classpath dependencies to all other dependencies............................................
     */
    private void addBootstrapClasspath() {
        final Collection<J2clDependency> bootstrapAndJreDependencies = this.collectBootstrapAndJreWithDependencies();

        this.dependencies()
                .stream()
                .filter(d -> false == d.isJreBootstrapClassFiles())
                .forEach(d -> {
                    d.add(bootstrapAndJreDependencies);
                });
    }

    /**
     * Returns all bootstrap and JRE and their dependencies.
     */
    private Collection<J2clDependency> collectBootstrapAndJreWithDependencies() {
        final Collection<J2clDependency> bootstrapAndJre = this.dependencies()
                .stream()
                .filter(J2clDependency::isAnnotationsBootstrapOrJreFiles)
                .collect(Collectors.toCollection(J2clDependency::set));

        final Collection<J2clDependency> dependencies = bootstrapAndJre.stream()
                .flatMap(d -> d.dependencies.stream())
                .filter(d -> false == d.isIgnored())
                .collect(Collectors.toCollection(J2clDependency::set));
        add(dependencies, bootstrapAndJre);

        bootstrapAndJre.addAll(dependencies);
        return bootstrapAndJre;
    }

    /**
     * Adds all the ifAbsents if they are absent from the all and includes sarts to avoid adding a dependency to itself.
     */
    private static void add(final Collection<J2clDependency> all, final Collection<J2clDependency> ifAbsent) {
        all.stream()
                .filter(d -> false == d.isIgnored() && false == ifAbsent.contains(d))
                .forEach(d -> d.add(ifAbsent));
    }

    /**
     * Adds any of the ifAbsent dependencies if they are new and not present in this, making a special case not to add itself.
     */
    private void add(final Collection<J2clDependency> ifAbsent) {
        for (final J2clDependency maybe : ifAbsent) {
            if(this.equals(maybe)) {
                continue;
            }
            if (this.dependencies.contains(maybe)) {
                continue;
            }
            this.dependencies.add(maybe);
        }
    }

    // verify...........................................................................................................

    /**
     * Checks that dependency coords do not have version conflicts, duplicates, and classpath required, javascript source
     * required and ignored dependency declarations.
     */
    private void verify() {
        final Collection<J2clDependency> all = this.dependencies();
        this.verifyWithoutConflictsOrDuplicates(all);

        this.context.verifyClasspathRequiredJavascriptSourceRequiredIgnoredDependencies(all
                        .stream()
                        .map(J2clDependency::coords)
                        .collect(Collectors.toCollection(Sets::sorted)),
                this);
    }

    /**
     * Verifies there are not dependencies that share the same group and artifact id but differ in other components.
     */
    private void verifyWithoutConflictsOrDuplicates(final Collection<J2clDependency> all) {
        final Set<J2clArtifactCoords> duplicateCoords = J2clArtifactCoords.set();
        final List<String> duplicatesText = Lists.array();

        for (final J2clDependency dependency : all) {
            final J2clArtifactCoords coords = dependency.coords();

            // must have been the duplicate of another coord.
            if (duplicateCoords.contains(coords)) {
                continue;
            }

            final List<J2clArtifactCoords> duplicates = all.stream()
                    .map(J2clDependency::coords)
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
            this.log(false);

            throw new IllegalStateException(duplicateCoords.size() + " duplicate(s)\n" + duplicatesText.stream().collect(Collectors.joining("\n")));
        }
    }

    /**
     * Updates the field returned by {@link #dependencies()} so it returns a read only {@link Set}.
     */
    private static void makeDependenciesGetterReadOnly(final Collection<J2clDependency> all) {
        all.forEach(d -> {
            d.dependencies = Sets.readOnly(d.dependencies);
        });
    }

    // print............................................................................................................

    /**
     * Prints a dependency graph and various metadata that will be used to plan the approach for building.
     * The printMetadata flag will be false when printing dependencies before a verify exception is thrown.
     */
    void log(final boolean includeMetadata) {
        final TreeLogger logger = this.context.mavenLogger()
                .output();
        logger.line("Dependencies");
        logger.indent();
        {
            this.logPrettyPrintDependencies(logger);

            if (includeMetadata) {
                this.logPlanMetadata(logger);
            }
        }
        logger.outdent();
        logger.flush();
    }

    /**
     * Pretty prints all dependencies with indentation.
     */
    private void logPrettyPrintDependencies(final TreeLogger logger) {
        logger.line("Graph");
        logger.indent();
        {

            for (final J2clDependency artifact : this.dependencies()) {
                logger.line(artifact.toString());
                logger.indent();
                {
                    for (final J2clDependency dependency : artifact.dependencies()) {
                        logger.line(dependency.toString());
                    }
                }
                logger.outdent();
            }
        }
        logger.outdent();
        logger.flush();
    }

    /**
     * Prints all groupings of artifact in alphabetical order.
     */
    private void logPlanMetadata(final TreeLogger logger) {
        logger.line("Metadata");
        logger.indent();
        {
            this.logDependencies("Annotation processor", J2clDependency::isAnnotationProcessor, logger);

            this.logDependencies("Annotation only class files", J2clDependency::isAnnotationClassFiles, logger);
            this.logDependencies("JRE bootstrap class files", J2clDependency::isJreBootstrapClassFiles, logger);
            this.logDependencies("JRE class files", J2clDependency::isJreClassFiles, logger);
            this.logDependencies("Javascript bootstrap class files", J2clDependency::isJreJavascriptBootstrapFiles, logger);
            this.logDependencies("Javascript class files", J2clDependency::isJreJavascriptFiles, logger);

            this.logDependencies("Classpath required", J2clDependency::isClasspathRequired, logger);
            this.logDependencies("Ignored dependencies", J2clDependency::isIgnored, logger);
            this.logDependencies("Javascript source required", J2clDependency::isJavascriptSourceRequired, logger);
        }
        logger.outdent();
        logger.flush();
    }

    private void logDependencies(final String label,
                                 final Predicate<J2clDependency> filter,
                                 final TreeLogger logger) {
        logger.strings(
                label,
                this.dependencies().stream()
                        .filter(filter)
                        .map(J2clDependency::toString)
                        .collect(Collectors.toCollection(Sets::sorted))
        );
    }

    // artifactFile......................................................................................................

    /**
     * Returns the archive file attached to this archive.
     */
    public synchronized Optional<J2clPath> artifactFile() {
        if (null == this.artifactFile) {
            final J2clArtifactCoords coords = this.coords();
            this.artifactFile = Optional.of(
                    this.context
                    .mavenMiddleware()
                    .mavenFile(coords.toString()
                    ).orElseThrow(this.archiveFileMissing()));
        }

        return this.artifactFile;
    }

    /**
     * Lazily load artifact file. This will be {@link Optional#empty()} for the project and will eventually resolve to a file for all dependencies.
     */
    private Optional<J2clPath> artifactFile;

    /**
     * Returns the archive file or fails if absent.
     */
    public J2clPath artifactFileOrFail() {
        return this.artifactFile().orElseThrow(this.archiveFileMissing());
    }

    private Supplier<IllegalArgumentException> archiveFileMissing() {
        return () -> new IllegalArgumentException("Archive file missing for " + CharSequences.quote(this.coords().toString()));
    }

    // coords...........................................................................................................

    public J2clArtifactCoords coords() {
        return this.coords;
    }

    /**
     * The coords containing the groupid, artifact-id, version and classifier
     */
    private final J2clArtifactCoords coords;

    // project...........................................................................................................

    public MavenProject project() {
        if (null == this.project) {
            // all requests with a null project must be dependencies of the project being built and their COMPILE dependencies should be fetched.
            final J2clMavenContext context = this.context;
            this.project = context.mavenMiddleware()
                    .mavenProject(
                            this.coords(),
                            J2clClasspathScope.COMPILE
                    );
        }
        return this.project;
    }

    private MavenProject project;

    // dependencies.....................................................................................................

    /**
     * Retrieves all dependencies including transients, and will include any required artifacts.
     */
    public Set<J2clDependency> dependencies() {
        return this.dependencies;
    }

    private Set<J2clDependency> dependencies = set();

    // isDependency.....................................................................................................

    public boolean isDependency() {
        return this.artifactFile().isPresent();
    }

    /**
     * Only returns true for artifacts that have been declared as classpath required or identified as an annotation
     * processor archive.
     */
    public boolean isClasspathRequired() {
        if (null == this.classpathRequired) {
            this.testArchive();

            final boolean required;

            do {
                if (this.isAnnotationClassFiles() || this.isAnnotationProcessor() || this.isJreBootstrapClassFiles() || this.isJreClassFiles()) {
                    required = true;
                    break;
                }

                if (this.isJreJavascriptBootstrapFiles() || this.isJreJavascriptFiles()) {
                    required = false;
                    break;
                }

                // classpath-required present
                if (this.classpathRequiredFile) {
                    required = true;
                    break;
                }

                // was included in POM javascript-source-required
                final J2clMavenContext context = this.context;
                final J2clArtifactCoords coords = this.coords();
                if (context.isClasspathRequired(coords)) {
                    required = true;
                    break;
                }

                // cp required but js missing so false
                if (this.javascriptSourceRequiredFile || context.isJavascriptSourceRequired(coords)) {
                    required = false;
                    break;
                }

                // both js and cp required missing default to required
                required = true;
            } while (false);

            this.classpathRequired = required;
        }
        return this.classpathRequired;
    }

    private Boolean classpathRequired;
    private boolean classpathRequiredFile;

    /**
     * Returns true for artifacts that only contain javascript.
     */
    public boolean isJavascriptSourceRequired() {
        if (null == this.javascriptSourceRequired) {
            this.testArchive();

            final boolean required;

            do {
                if (this.isAnnotationClassFiles() || this.isAnnotationProcessor() || this.isJreBootstrapClassFiles() || this.isJreClassFiles()) {
                    required = false;
                    break;
                }

                if (this.isJreJavascriptBootstrapFiles() || this.isJreJavascriptFiles()) {
                    required = true;
                    break;
                }

                // javascript-source-required present
                if (this.javascriptSourceRequiredFile) {
                    required = true;
                    break;
                }

                // was included in POM javascript-source-required
                final J2clMavenContext context = this.context;
                final J2clArtifactCoords coords = this.coords();
                if (context.isJavascriptSourceRequired(coords)) {
                    required = true;
                    break;
                }

                // cp required but js missing so false
                if (this.classpathRequiredFile || context.isClasspathRequired(coords)) {
                    required = false;
                    break;
                }

                // both js and cp required missing default to required
                required = true;
            } while (false);

            this.javascriptSourceRequired = required;
        }

        return this.javascriptSourceRequired;
    }

    private Boolean javascriptSourceRequired;
    private boolean javascriptSourceRequiredFile;

    /**
     * Only files marked with an ignore file or in the POM under the ignored-dependencies will return true.
     */
    public boolean isIgnored() {
        if (null == this.ignored) {
            this.testArchive();

            this.ignored = this.ignoredFile || this.context.isIgnored(this.coords());
        }
        return this.ignored;
    }

    private Boolean ignored;
    private Boolean ignoredFile;

    // isAnnotationClassFiles...........................................................................................

    /**
     * Returns true if this dependency only includes annotation class files.
     */
    public boolean isAnnotationClassFiles() {
        if (null == this.annotationClassFiles) {
            this.testArchive();
        }

        return this.annotationClassFiles;
    }

    private Boolean annotationClassFiles;

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
    public boolean isAnnotationProcessor() {
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
            this.isJreJavascriptBootstrapFiles() ||
            this.isJreJavascriptFiles();
    }

    // isJreJavascriptBootstrapFiles..................................................................................................

    /**
     * Returns true if this archive contains JAVASCRIPT bootstrap class files, by testing if java.lang.Class class file exists.
     */
    public boolean isJreJavascriptBootstrapFiles() {
        if (null == this.jreJavascriptBootstrapFiles) {
            this.testArchive();
        }

        return this.jreJavascriptBootstrapFiles;
    }

    private Boolean jreJavascriptBootstrapFiles;

    // isJreJavascriptFiles..................................................................................................

    /**
     * Returns true if this archive contains JAVASCRIPT  files, by testing if java.lang.  file exists.
     */
    public boolean isJreJavascriptFiles() {
        if (null == this.jreJavascriptFiles) {
            this.testArchive();
        }

        return this.jreJavascriptFiles;
    }

    private Boolean jreJavascriptFiles;

    // isJreBootstrapClassFiles..................................................................................................

    /**
     * Returns true if this archive contains JRE bootstrap class files, by testing if java.lang.Class class file exists.
     */
    public boolean isJreBootstrapClassFiles() {
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
    public boolean isJreClassFiles() {
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
        final boolean jreJavascriptBootstrapFiles;
        final boolean jreJavascriptFiles;
        final boolean javascriptSourceRequiredFile;
        final boolean jreBootstrapClassFiles;
        final boolean jreClassFiles;

        final J2clPath file = this.artifactFile().orElse(null);
        if (null != file) {
            try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.file().toURI()), Collections.emptyMap())) {
                annotationProcessor = Files.exists(zip.getPath(META_INF_SERVICES_PROCESSOR));

                classpathRequiredFile = Files.exists(zip.getPath(CLASSFILE_REQUIRED));

                ignoredFile = Files.exists(zip.getPath(IGNORED_DEPENDENCY));

                jreJavascriptBootstrapFiles = Files.exists(zip.getPath(JAVASCRIPT_BOOTSTRAP));
                jreJavascriptFiles = Files.exists(zip.getPath(JAVASCRIPT_FILE));

                javascriptSourceRequiredFile = Files.exists(zip.getPath(JAVASCRIPT_SOURCE_REQUIRED));

                jreBootstrapClassFiles = Files.exists(zip.getPath(JAVA_BOOTSTRAP_CLASSFILE));
                jreClassFiles = Files.exists(zip.getPath(JAVA_CLASSFILE));

                if (false == (annotationProcessor || jreJavascriptBootstrapFiles || jreJavascriptFiles || jreBootstrapClassFiles || jreClassFiles)) {
                    final boolean[] annotations = new boolean[1];

                    Files.walkFileTree(zip.getPath("/"),
                            new SimpleFileVisitor<>() {

                                @Override
                                public FileVisitResult visitFile(final Path file,
                                                                 final BasicFileAttributes attrs) throws IOException {
                                    final FileVisitResult result;

                                    if (J2clPath.WITHOUT_META_INF.test(file) && J2clPath.CLASS_FILES.test(file)) {
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
                                    } else {
                                        result = FileVisitResult.CONTINUE;
                                    }

                                    return result;
                                }
                            });
                    annotationClassFiles = annotations[0];
                } else {
                    annotationClassFiles = false;
                }
            } catch (final IOException | ProviderNotFoundException cause) {
                throw new J2clException("Failed reading archive " + CharSequences.quote(file.toString()) + " while trying to test", cause);
            }
        } else {
            annotationClassFiles = false;
            annotationProcessor = false;

            classpathRequiredFile = false;

            ignoredFile = false;

            jreJavascriptBootstrapFiles = false;
            jreJavascriptFiles = false;

            javascriptSourceRequiredFile = false;

            jreBootstrapClassFiles = false;
            jreClassFiles = false;
        }

        this.annotationClassFiles = annotationClassFiles;
        this.annotationProcessor = annotationProcessor;

        this.classpathRequiredFile = classpathRequiredFile;

        this.ignoredFile = ignoredFile;

        this.jreJavascriptBootstrapFiles = jreJavascriptBootstrapFiles;
        this.jreJavascriptFiles = jreJavascriptFiles;

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
    private final static String META_INF_SERVICES_PROCESSOR = "/META-INF/services/" + javax.annotation.processing.Processor.class.getName();

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

    // shade............................................................................................................

    public Map<String, String> shadeMappings() throws IOException {
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

    // directories......................................................................................................

    /**
     * Sets the directory for this dependency, assumes the hash has been computed
     */
    public J2clDependency setDirectory(final String hash) throws IOException {
        final J2clPath create = this.context.cache()
                .append(
                        this.coords.directorySafeName() + "-" + hash
                );
        final J2clPath previous = this.directory.compareAndExchange(null, create);
        if (null != previous) {
            throw new IllegalStateException("Hash already set for this artifact: " + CharSequences.quote(create.toString()));
        }

        create.append(this.context.directoryName(J2clStep.HASH))
                .createIfNecessary();
        return this;
    }

    /**
     * Getter that returns the base directory for this artifact which includes the output for after preparation.
     */
    J2clPath directory() {
        final J2clPath directory = this.directory.get();
        if (null == directory) {
            throw new IllegalStateException(
                    "Directory under " +
                            this.context.cache() +
                            " missing for " +
                            CharSequences.quote(
                                    this.coords()
                                            .toString()
                            )
            );
        }
        return directory;
    }

    private final AtomicReference<J2clPath> directory = new AtomicReference<>();

    /**
     * Returns a compile step directory, creating it if necessary.
     */
    public J2clStepDirectory step(final J2clStep step) {
        return J2clStepDirectory.with(
                Paths.get(
                        this.directory().toString(),
                        this.context.directoryName(step)
                )
        );
    }

    /**
     * Tries to find the output directory for the given {@link J2clStep} stopping when one is found or returns
     * the archive file for this dependency.
     */
    public J2clPath stepSourcesOrArchiveFile(final List<J2clStep> steps) {
        Objects.requireNonNull(steps, "steps");

        J2clPath result = null;

        final J2clPath directory = this.directory.get();
        if (null != directory) {
            result = steps.stream()
                    .flatMap(s -> this.step(s).output().exists().stream())
                    .findFirst()
                    .orElse(null);
        }

        return null != result ?
                result :
                this.artifactFileOrFail();
    }

    // maven ...........................................................................................................

    /**
     * Returns all source roots including resources which can be directories or archives.
     */
    public List<J2clPath> sourcesRoot() {
        final J2clMavenContext context = this.context;
        final List<J2clPath> sources = Lists.array();

        final MavenProject project = this.project();
        final File projectBase = project.getFile();
        if (null != projectBase) {
            sources.addAll(context
                    .sourcesKind()
                    .compileSourceRoots(project, J2clPath.with(projectBase.toPath())));
        }
        // no project source try and sources archive and then jar file itself
        if(sources.isEmpty()) {
            final J2clArtifactCoords coords = this.coords();
            final J2clMavenMiddleware middleware = context.mavenMiddleware();

            middleware.mavenFile(coords.source().toString()).map(sources::add);

            if(sources.isEmpty()) {
                middleware.mavenFile(coords.toString()).map(sources::add);
            }
        }

        return Lists.readOnly(sources);
    }

    private final J2clMavenContext context;

    // Object.........................................................................................................

    @Override
    public int hashCode() {
        return this.coords().hashCode();
    }

    public boolean equals(final Object other) {
        return this == other || other instanceof J2clDependency && this.equals0((J2clDependency)other);
    }

    private boolean equals0(final J2clDependency other) {
        return this.coords().equals(other.coords());
    }

    // toString.........................................................................................................
    @Override
    public String toString() {
        return this.coords().toString();
    }

    // Comparable.......................................................................................................

    @Override
    public int compareTo(final J2clDependency other) {
        return this.coords().compareTo(other.coords());
    }
}
