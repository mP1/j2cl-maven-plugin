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


import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class that captures maven plugin parameters and methods common to both build and test.
 */
abstract class J2clMojoBuildTest extends J2clMojo {

    J2clMojoBuildTest() {
        super();
    }

    /**
     * The {@link J2clRequest} accompanying the build.
     */
    final J2clRequest request(final List<String> entryPoints,
                              final J2clPath initialScriptFilename) {
        return J2clRequest.with(this.cache(),
                this.output(),
                this.classpathScope(),
                this.addedDependencies(),
                this.classpathRequired(),
                this.excludedDependencies(),
                this.javascriptSourceRequired(),
                this.processingSkipped(),
                this.replacedDependencies(),
                this.sourcesKind(),
                this.compilationLevel(),
                this.defines(),
                this.externs(),
                entryPoints,
                this.formatting(),
                initialScriptFilename,
                this.languageOut(),
                this.mavenMiddleware(),
                this.executor(),
                this.logger());
    }

    abstract J2clSourcesKind sourcesKind();

    // MAVEN............................................................................................................

    // classpathScope...................................................................................................

    private J2clClasspathScope classpathScope() {
        return J2clClasspathScope.commandLineOption(this.classpathScope);
    }

    /**
     * The scope to use when picking dependencies to pass to the Closure Compiler.
     * <p>The scope should be one of the scopes defined by org.apache.maven.artifact.Artifact. This includes the following:
     * <ul>
     * <li><i>compile</i> - system, provided, compile
     * <li><i>runtime</i> - compile, runtime
     * <li><i>compile+runtime</i> - system, provided, compile, runtime
     * <li><i>runtime+system</i> - system, compile, runtime
     * <li><i>test</i> - system, provided, compile, runtime, test
     * </ul>
     * <br>
     * <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html">Classpath scope</a>
     */
    @Parameter(alias = "classpath-scope", required = true)
    private String classpathScope;

    // output...........................................................................................................

    /**
     * The output directory where the final product of the build is copied to.
     */
    private J2clPath output() {
        return J2clPath.with(this.output.toPath());
    }

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}",
            required = true)
    private File output;


    // javascriptSourceRequired........................................................................................

    private List<J2clArtifactCoords> javascriptSourceRequired() {
        return parseList(this.javascriptSourceRequired);
    }

    /**
     * List of artifacts required when javascript sources are passed as arguments.
     */
    @Parameter(alias = "javascript-source-required", required = true)
    private List<String> javascriptSourceRequired;

    // processingSkipped................................................................................................

    private List<J2clArtifactCoords> processingSkipped() {
        return parseList(this.processingSkipped);
    }

    /**
     * List of artifacts that will not be processed at all. When required on the java classpath or source,
     * the archive file will be added.
     */
    @Parameter(alias = "processing-skipped", required = true)
    private List<String> processingSkipped;

    private List<J2clArtifactCoords> parseList(final List<String> coords) {
        return coords.stream()
                .map(J2clArtifactCoords::parse)
                .collect(Collectors.toList());
    }

    // DEPENDENCIES.....................................................................................................

    /**
     * Gathers the all the dependencies.
     */
    final J2clDependency gatherDependencies(final J2clRequest request) {
        return J2clDependency.gather(this.project, request);
    }

    // autoAddedDependencies.............................................................................................

    private Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies() {
        final Map<J2clArtifactCoords, List<J2clArtifactCoords>> lookup = Maps.sorted();

        for (final String mapping : this.addedDependencies) {
            final int equalsSign = mapping.indexOf('=');
            if (-1 == equalsSign) {
                throw new IllegalArgumentException("Replacement dependency missing '=' in " + CharSequences.quoteAndEscape(mapping));
            }

            final String autoAdded = mapping.substring(equalsSign + 1);

            lookup.put(J2clArtifactCoords.parse(mapping.substring(0, equalsSign)), Arrays.stream(autoAdded.split(","))
                    .map(String::trim)
                    .map(J2clArtifactCoords::parse)
                    .collect(Collectors.toList())
            );
        }

        return Maps.readOnly(lookup);
    }

    /**
     * A {@link List} of parent artifact to a comma separated list of dependencies to auto add, with the former separated
     * from the later by an equals sign.
     */
    @Parameter(alias = "added-dependencies", required = true)
    private List<String> addedDependencies = Lists.array();

    // excludedDependencies..............................................................................................

    /**
     * A {@link Predicate} that matches any transitive dependencies that should be removed from the dependency graph.
     * This should be applied during the dependency discover phase.
     */
    private Predicate<J2clArtifactCoords> excludedDependencies() {
        final List<Predicate<J2clArtifactCoords>> filter = this.excludedDependencies.stream()
                .map(String::trim)
                .map(this::groupArtifactAndVersionPredicate)
                .collect(Collectors.toList());

        return new Predicate<>() {
            @Override
            public boolean test(final J2clArtifactCoords coords) {
                boolean exclude = false;

                for (final Predicate<J2clArtifactCoords> possible : filter) {
                    exclude = possible.test(coords);
                    if (exclude) {
                        break;
                    }
                }

                return exclude;
            }

            @Override
            public String toString() {
                return String.join(",", J2clMojoBuildTest.this.excludedDependencies);
            }
        };
    }

    /**
     * Only matches using the group-id and artifact-id the base or original version not the resolved version is ignored.
     */
    private Predicate<J2clArtifactCoords> groupArtifactAndVersionPredicate(final String coords) {
        final String[] components = coords.split(":");
        if (3 != components.length) {
            throw new IllegalArgumentException("Invalid coords, expected 3 components (groupId, artifactId, version): " + CharSequences.quoteAndEscape(coords));
        }

        final String groupId = components[0];
        final String artifactId = components[1];
        final String version = components[2];

        CharSequences.failIfNullOrEmpty(groupId, "group-id");
        CharSequences.failIfNullOrEmpty(artifactId, "artifact-id");
        CharSequences.failIfNullOrEmpty(version, "version");

        return new Predicate<>() {

            @Override
            public boolean test(final J2clArtifactCoords test) {
                return groupId.equals(test.groupId()) && artifactId.equals(test.artifactId()) && version.equals(test.version());
            }

            @Override
            public String toString() {
                return coords;
            }
        };
    }

    @Parameter(alias = "excluded-dependencies", required = true)
    private List<String> excludedDependencies = new ArrayList<>();

    // replacedDependencies.............................................................................................

    private Map<J2clArtifactCoords, J2clArtifactCoords> replacedDependencies() {
        final Map<J2clArtifactCoords, J2clArtifactCoords> lookup = Maps.sorted();

        for (final String mapping : this.replacedDependencies) {
            final String mapping2 = mapping.trim();
            final int equalsSign = mapping2.indexOf('=');
            if (-1 == equalsSign) {
                throw new IllegalArgumentException("Replacement dependency missing '=' in " + CharSequences.quoteAndEscape(mapping2));
            }
            lookup.put(J2clArtifactCoords.parse(mapping2.substring(0, equalsSign)), J2clArtifactCoords.parse(mapping2.substring(equalsSign + 1)));
        }

        return Maps.readOnly(lookup);
    }

    /**
     * A list of artifact to artifact separated by equals sign. A {@link Map} cant be used because of maven coords
     * containing a colon.
     */
    @Parameter(alias = "replaced-dependencies", required = true)
    private List<String> replacedDependencies = Lists.array();

    // JAVA.............................................................................................................

    // classpathRequired................................................................................................

    private List<J2clArtifactCoords> classpathRequired() {
        return parseList(this.classpathRequired);
    }

    /**
     * List of artifacts required on java classpaths.
     */
    @Parameter(alias = "classpath-required", required = true)
    private List<String> classpathRequired;

    // CLOSURE..........................................................................................................

    // compilationLevel.................................................................................................

    private CompilationLevel compilationLevel() {
        final String parameter = this.compilationLevel.trim();
        final CompilationLevel level = CompilationLevel.fromString(parameter);
        if (null == level) {
            throw new IllegalStateException("Invalid compilation-level was " + CharSequences.quoteAndEscape(parameter));
        }
        return level;
    }

    /**
     * <a href="https://developers.google.com/closure/compiler/docs/compilation_levels">Compilation levels</a>
     */
    @Parameter(alias = "compilation-level",
            required = true)
    private String compilationLevel;

    // defines..........................................................................................................

    private Map<String, String> defines() {
        return this.defines;
    }

    @Parameter(required = true)
    private Map<String, String> defines = new HashMap<>();

    // externs..........................................................................................................

    /**
     * Externs sorted alphabetically, aides pretty printing.
     */
    private SortedSet<String> externs() {
        return new java.util.TreeSet<>(this.externs);
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(required = true)
    private List<String> externs = new ArrayList<>();


    // formatting..........................................................................................................

    /**
     * The formatting options if any are set.
     */
    private Set<ClosureFormattingOption> formatting() {
        return this.formatting.stream()
                .map(String::trim)
                .map(ClosureFormattingOption::fromCommandLine)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ClosureFormattingOption.class)));
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(required = true)
    private List<String> formatting = new ArrayList<>();

    // language-out.....................................................................................................

    private LanguageMode languageOut() {
        return this.languageOut;
    }

    @Parameter(alias = "language-out",
            required = true)
    private LanguageMode languageOut;


    @Parameter(defaultValue = "${project}",
            readonly = true,
            required = true)
    private MavenProject project;

    // threadPool.......................................................................................................

    private ExecutorService executor() {
        final int threadPoolSize = this.threadPoolSize;
        if (threadPoolSize < 0) {
            throw new IllegalStateException("Invalid threadPoolSize expected 0 to select CPU cores *2, or a positive value but got " + threadPoolSize);
        }

        return Executors.newFixedThreadPool(0 != threadPoolSize ?
                threadPoolSize :
                Runtime.getRuntime().availableProcessors() * 2);
    }

    /**
     * If a value of zero is passed or defaulted the a thread pool equal to the CPU core count * 2 is created.
     * <br>
     * It may be useful to set this value to 1 to aide ordering and have console output in an ordered non interrupted
     * single thread.
     */
    @Parameter(alias = "thread-pool-size",
            readonly = true,
            required = true)
    private int threadPoolSize;

    // mavenMiddleware..................................................................................................

    /**
     * Factory that creates a {@link J2clMavenMiddleware}
     */
    private J2clMavenMiddleware mavenMiddleware() {
        if (null == this.mavenMiddleware) {
            this.mavenMiddleware = J2clMavenMiddleware.of(this.mavenSession,
                    this.repositorySystem,
                    this.repositorySession,
                    this.repositories,
                    this.projectBuilder,
                    this.project);

        }
        return this.mavenMiddleware;
    }

    private J2clMavenMiddleware mavenMiddleware;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}",
            readonly = true,
            required = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}",
            readonly = true,
            required = true)
    private List<RemoteRepository> repositories;
}
