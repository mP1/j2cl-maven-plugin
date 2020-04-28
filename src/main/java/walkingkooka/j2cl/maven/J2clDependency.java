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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a single artifact including the project or any of its dependencies. These will in turn have java files etc.
 */
final class J2clDependency implements Comparable<J2clDependency> {

    static Set<J2clDependency> set() {
        return Sets.sorted();
    }

    /**
     * Gathers all dependencies honouring excludes and dependencyManagement entries in POMs.
     */
    static J2clDependency gather(final MavenProject project,
                                 final J2clRequest request) {
        final J2clDependency root = new J2clDependency(J2clArtifactCoords.with(project.getArtifact()),
                project,
                Optional.empty(),
                request);
        root.gather(request.scope().scopeFilter(), Predicates.never(), Function.identity());
        root.verify();
        root.addBootstrapClasspath();
        root.print(true);

        return root;
    }

    /**
     * Holds all dependencies discovered during the gathering process. This will be used to verify that lists like
     * classpathRequired actually match at least one real dependency.
     */
    private final static List<J2clDependency> all = Lists.array();

    // ctor.............................................................................................................

    private J2clDependency(final J2clArtifactCoords coords,
                           final MavenProject project,
                           final Optional<J2clPath> artifactFile,
                           final J2clRequest request) {
        super();
        this.coords = coords;
        this.project = project;
        this.artifactFile = artifactFile;
        this.request = request;

        J2clDependency.all.add(this);
    }

    private void gather(final Predicate<String> scopeFilter,
                        final Predicate<J2clArtifactCoords> parentExclusions,
                        final Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement) {
        this.gather0(scopeFilter, parentExclusions, this.dependencyManagement(dependencyManagement));
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

    private void gather0(final Predicate<String> scopeFilter,
                            final Predicate<J2clArtifactCoords> parentExclusions,
                            final Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement) {
        final List<Predicate<J2clArtifactCoords>> childExclusions = Lists.array();

        this.gatherChildrenDependencies(scopeFilter, parentExclusions, dependencyManagement, childExclusions);
        this.gatherDescendants(scopeFilter, childExclusions, dependencyManagement);
        this.addChildrenDependencies();
    }

    private void gatherChildrenDependencies(final Predicate<String> scopeFilter,
                                            final Predicate<J2clArtifactCoords> parentExclusions,
                                            final Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement,
                                            final List<Predicate<J2clArtifactCoords>> childExclusions) {
        J2clRequest request = this.request();
        final J2clClasspathScope scope = request.scope();
        final MavenProject project = this.project();
        final J2clMavenMiddleware middleware = request.mavenMiddleware();

        for (final Dependency dependency : project.getDependencies()) {
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
            final J2clArtifactCoords corrected = dependencyManagement.apply(coords);
            final MavenProject childProject = middleware.mavenProject(corrected.mavenArtifact(scope, middleware.artifactHandler(coords.typeOrDefault())));

            this.dependencies.add(new J2clDependency(corrected,
                    childProject,
                    Optional.of(middleware.mavenFile(corrected.toString()).orElseThrow(() -> new IllegalArgumentException("Archive file missing for " + CharSequences.quote(corrected.toString())))),
                    request));

            childExclusions.add(exclusions(parentExclusions, dependency));
        }
    }

    private void gatherDescendants(final Predicate<String> scopeFilter,
                                   final List<Predicate<J2clArtifactCoords>> childExclusions,
                                   final Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagement) {
        final Iterator<Predicate<J2clArtifactCoords>> exclusionsIterator = childExclusions.iterator();
        for (final J2clDependency child : this.dependencies) {
            child.gather(scopeFilter,
                    exclusionsIterator.next(),
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

    private void addChildrenDependencies() {
        // temp copy to avoid ConcurrentModificationException
        final List<J2clDependency> dependencies = Lists.array();
        for (final J2clDependency child : this.dependencies) {
            dependencies.addAll(child.dependencies);
        }
        this.dependencies.addAll(dependencies);
    }

    /**
     * Checks that dependency coords do not have version conflicts, duplicates, and classpath required, javascript source
     * required and ignored dependency declarations.
     */
    private void verify() {
        this.verifyWithoutConflictsOrDuplicates();
        this.request().verifyClasspathRequiredJavascriptSourceRequiredIgnoredDependencies(J2clDependency.all
                .stream()
                .map(J2clDependency::coords)
                .collect(Collectors.toCollection(Sets::sorted)),
                this);
    }

    /**
     * Verifies there are not dependencies that share the same group and artifact id but differ in other components.
     */
    private void verifyWithoutConflictsOrDuplicates() {
        final Set<J2clArtifactCoords> duplicateCoords = J2clArtifactCoords.set();
        final List<String> duplicatesText = Lists.array();

        for (final J2clDependency dependency : J2clDependency.all) {
            final J2clArtifactCoords coords = dependency.coords();

            // must have been the duplicate of another coord.
            if (duplicateCoords.contains(coords)) {
                continue;
            }

            final List<J2clArtifactCoords> duplicates = J2clDependency.all.stream()
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
            this.print(false);

            throw new IllegalStateException(duplicateCoords.size() + " duplicate(s)\n" + duplicatesText.stream().collect(Collectors.joining("\n")));
        }
    }

    /**
     * Add the bootstrap and classpath dependencies to all other dependencies............................................
     */
    private void addBootstrapClasspath() {
        final Map<J2clArtifactCoords, J2clDependency> coordToDependency = gatherCoordToDependency();

        // some coords will have multiple J2clDependency instances replace them all.
        final Set<J2clDependency> dependencies = set();
        for (final J2clDependency dependency : coordToDependency.values()) {
            final Set<J2clDependency> singletons = set();
            for (final J2clDependency child : dependency.dependencies) {
                final J2clDependency childSingleton = coordToDependency.get(child.coords());
                if (null == childSingleton) {
                    throw new NullPointerException("Missing " + child.coords() + "\n" + coordToDependency.values());
                }

                singletons.add(childSingleton);
                dependencies.add(childSingleton);
            }
            dependency.dependencies2 = singletons;
            dependency.dependencies = null; // no longer needed
        }

        final Collection<J2clDependency> bootstrapAndJreDependencies = collectBootstrapAndJreWithDependencies(coordToDependency.values());
        addIfAbsent(coordToDependency.values(), bootstrapAndJreDependencies);
        makeDependenciesGetterReadOnly(coordToDependency.values());
    }

    /**
     * Build a {@link Map} holding a mapping of coordinates to dependency. This assumes that all dependencies with the same coords are equivalent.
     */
    private Map<J2clArtifactCoords, J2clDependency> gatherCoordToDependency() {
        final Map<J2clArtifactCoords, J2clDependency> coordToDependency = Maps.sorted(J2clArtifactCoords.IGNORE_VERSION_COMPARATOR);
        coordToDependency.put(this.coords(), this);

        for (final J2clDependency dependency : this.dependencies) {
            coordToDependency.put(dependency.coords(), dependency);
        }

        return coordToDependency;
    }

    /**
     * Returns all bootstrap and JRE and their dependencies.
     */
    private static Collection<J2clDependency> collectBootstrapAndJreWithDependencies(final Collection<J2clDependency> all) {
        final Collection<J2clDependency> bootstrapAndJre = all.stream()
                .filter(J2clDependency::isAnnotationsBootstrapOrJreFiles)
                .collect(Collectors.toCollection(J2clDependency::set));

        final Collection<J2clDependency> dependencies = bootstrapAndJre.stream()
                .flatMap(d -> d.dependencies2.stream())
                .filter(d -> false == d.isIgnored())
                .collect(Collectors.toCollection(J2clDependency::set));
        addIfAbsent(dependencies, bootstrapAndJre);

        bootstrapAndJre.addAll(dependencies);
        return bootstrapAndJre;
    }

    /**
     * Adds all the ifAbsents if they are absent from the all
     */
    private static void addIfAbsent(final Collection<J2clDependency> all, final Collection<J2clDependency> ifAbsent) {
        all.stream()
                .filter(d -> false == d.isIgnored() && false == ifAbsent.contains(d))
                .forEach(d -> d.addIfAbsent(ifAbsent));
    }

    /**
     * Adds any of the ifAbsent dependencies if they are new and not present in this.
     */
    private void addIfAbsent(final Collection<J2clDependency> ifAbsent) {
        for (final J2clDependency maybe : ifAbsent) {
            if (false == this.dependencies2.contains(maybe)) {
                this.dependencies2.add(maybe);
            }
        }
    }

    /**
     * Updates the field returned by {@link #dependencies()} so it returns a read only {@link Set}.
     */
    private static void makeDependenciesGetterReadOnly(final Collection<J2clDependency> all) {
        all.forEach(d -> {
            d.dependencies2 = Sets.readOnly(d.dependencies2);
        });
    }

    // print............................................................................................................

    /**
     * Prints a dependency graph and various metadata that will be used to plan the approach for building.
     * The printMetadata flag will be false when printing dependencies before a verify exception is thrown.
     */
    void print(final boolean printMetadata) {
        final J2clLogger logger = this.request.logger();
        final J2clLinePrinter printer = J2clLinePrinter.with(logger.printer(logger::info), null);
        printer.printLine("Dependencies");
        printer.indent();
        {
            if (false == printMetadata) {
                this.expandDependencies(); // @see #expandDepenencies
            }
            this.prettyPrintDependencies(printer);

            if (printMetadata) {
                this.printPlanMetadata(printer);
            }
        }
        printer.outdent();
        printer.flush();
    }

    /**
     * This is necessary so {@link #prettyPrintDependencies(J2clLinePrinter)} will not fail because {@link #dependencies2} will be null.
     */
    private void expandDependencies() {
        final Map<J2clArtifactCoords, J2clDependency> coordToDependency = gatherCoordToDependency();

        // some coords will have multiple J2clDependency instances replace them all.
        final Set<J2clDependency> dependencies = set();
        for (final J2clDependency dependency : coordToDependency.values()) {
            final Set<J2clDependency> singletons = set();
            for (final J2clDependency child : dependency.dependencies) {
                final J2clDependency childSingleton = coordToDependency.get(child.coords());
                if (null == childSingleton) {
                    continue;
                }

                singletons.add(childSingleton);
                dependencies.add(childSingleton);
            }
            dependency.dependencies2 = singletons;
            dependency.dependencies = null; // no longer needed
        }
    }

    /**
     * Pretty prints all dependencies with indentation.
     */
    private void prettyPrintDependencies(final J2clLinePrinter printer) {
        printer.printLine("Graph");
        printer.indent();
        {

            for (final J2clDependency artifact : this.dependencies()) {
                printer.printLine(artifact.toString());
                printer.indent();
                {
                    for (final J2clDependency dependency : artifact.dependencies()) {
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
            this.print("JRE bootstrap class files", J2clDependency::isJreBootstrapClassFiles, printer);
            this.print("JRE class files", J2clDependency::isJreClassFiles, printer);
            this.print("Javascript bootstrap class files", J2clDependency::isJreJavascriptBootstrapFiles, printer);
            this.print("Javascript class files", J2clDependency::isJreJavascriptFiles, printer);

            this.print("Classpath required", J2clDependency::isClasspathRequired, printer);
            this.print("Ignored dependencies", J2clDependency::isIgnored, printer);
            this.print("Javascript source required", J2clDependency::isJavascriptSourceRequired, printer);
        }
        printer.outdent();
        printer.flush();
    }

    private void print(final String label,
                       final Predicate<J2clDependency> filter,
                       final J2clLinePrinter printer) {

        printer.printIndentedString(label,
                this.dependencies().stream()
                        .filter(filter)
                        .map(J2clDependency::toString)
                        .collect(Collectors.toList()));
    }

    // artifactFile......................................................................................................

    /**
     * Returns the archive file attached to this archive.
     */
    Optional<J2clPath> artifactFile() {
        return this.artifactFile;
    }

    /**
     * Returns the archive file or fails if absent.
     */
    J2clPath artifactFileOrFail() {
        return this.artifactFile().orElseThrow(() -> new IllegalArgumentException("Archive file missing for " + CharSequences.quote(this.coords().toString())));
    }

    private final Optional<J2clPath> artifactFile;

    // coords...........................................................................................................

    J2clArtifactCoords coords() {
        return this.coords;
    }

    /**
     * The coords containing the groupid, artifact-id, version and classifier
     */
    private final J2clArtifactCoords coords;

    // project...........................................................................................................

    MavenProject project() {
        return this.project;
    }

    private MavenProject project;

    // dependencies.....................................................................................................

    /**
     * Retrieves all dependencies including transients, and will include any required artifacts.
     */
    Set<J2clDependency> dependencies() {
        return this.dependencies2;
    }

    // may contain duplicates...
    private List<J2clDependency> dependencies = Lists.array();

    // without duplicates
    private Set<J2clDependency> dependencies2;

    // isDependency.....................................................................................................

    boolean isDependency() {
        return this.artifactFile().isPresent();
    }

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
                    this.isJreJavascriptBootstrapFiles() ||
                    this.isJreJavascriptFiles() ||
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
                    this.isJreJavascriptBootstrapFiles() ||
                    this.isJreJavascriptFiles() ||
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
    boolean isAnnotationProcessor() {
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
    boolean isJreJavascriptBootstrapFiles() {
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
    boolean isJreJavascriptFiles() {
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
        final J2clRequest request = this.request();
        final List<J2clPath> sources = Lists.array();

        final MavenProject project = this.project();
        final File projectBase = project.getFile();
        if(null != projectBase) {
            sources.addAll(request
                    .sourcesKind()
                    .compileSourceRoots(project, J2clPath.with(projectBase.toPath())));
        }
        // no project source try and sources archive and then jar file itself
        if(sources.isEmpty()) {
            final J2clArtifactCoords coords = this.coords();
            final J2clMavenMiddleware middleware = request.mavenMiddleware();

            middleware.mavenFile(coords.source().toString()).map(sources::add);

            if(sources.isEmpty()) {
                middleware.mavenFile(coords.toString()).map(sources::add);
            }
        }

        return Lists.readOnly(sources);
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
