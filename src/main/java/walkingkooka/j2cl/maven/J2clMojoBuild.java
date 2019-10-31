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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * Builds the given project and all of its dependencies in the correct order producing a single JS file.
 */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public final class J2clMojoBuild extends J2clMojo {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final J2clBuildRequest request = this.request();
            request.execute(J2clDependency.gather(this.project, request));
        } catch (final Throwable e) {
            throw new MojoExecutionException("Failed to build project, check logs above", e);
        }
    }

    private J2clBuildRequest request() {
        return J2clBuildRequest.with(this.addedDependencies(),
                this.classpathScope(),
                this.compilationLevel(),
                this.defines(),
                this.externs(),
                this.entryPoints(),
                this.initialScriptFilename(),
                this.cache(),
                this.isJavacBootstrap(),
                this.isJre(),
                this.excludedDependencies(),
                this.replacedDependencies(),
                J2clSourcesKind.SRC,
                this.output(),
                this.mavenMiddleware(),
                this.executor(),
                this.logger());
    }

    // autoAddedDependencies.............................................................................................

    private Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies() {
        final Map<J2clArtifactCoords, List<J2clArtifactCoords>> lookup = Maps.sorted();

        for (final String mapping : this.addedDependencies) {
            final int equalsSign = mapping.indexOf('=');
            if (-1 == equalsSign) {
                throw new IllegalArgumentException("Replacement dependency missing '=' in " + CharSequences.quoteAndEscape(mapping));
            }

            final String autoAdded =mapping.substring(equalsSign + 1);

            lookup.put(J2clArtifactCoords.parse(mapping.substring(0, equalsSign)), Arrays.stream(autoAdded.split(","))
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

    // compilationLevel.................................................................................................

    private CompilationLevel compilationLevel() {
        final String parameter = this.compilationLevel;
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

    // entry-points.....................................................................................................

    private List<String> entryPoints() {
        return this.entrypoints;
    }

    @Parameter(alias = "entry-points", required = true)
    private List<String> entrypoints = new ArrayList<>();

    // excludedDependencies..............................................................................................

    /**
     * A {@link Predicate} that matches any transitive dependencies that should be removed from the dependency graph.
     * This should be applied during the dependency discover phase.
     */
    private Predicate<J2clArtifactCoords> excludedDependencies() {
        final List<Predicate<J2clArtifactCoords>> filter = this.excludedDependencies.stream()
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
                return J2clMojoBuild.this.excludedDependencies.stream().collect(Collectors.joining(","));
            }
        };
    }

    @Parameter(alias = "excluded-dependencies", required = true)
    private List<String> excludedDependencies = new ArrayList<>();

    // replacedDependencies.............................................................................................

    Map<J2clArtifactCoords, J2clArtifactCoords> replacedDependencies() {
        final Map<J2clArtifactCoords, J2clArtifactCoords> lookup = Maps.sorted();

        for (final String mapping : this.replacedDependencies) {
            final int equalsSign = mapping.indexOf('=');
            if (-1 == equalsSign) {
                throw new IllegalArgumentException("Replacement dependency missing '=' in " + CharSequences.quoteAndEscape(mapping));
            }
            lookup.put(J2clArtifactCoords.parse(mapping.substring(0, equalsSign)), J2clArtifactCoords.parse(mapping.substring(equalsSign + 1)));
        }

        return Maps.readOnly(lookup);
    }

    /**
     * A list of artifact to artifact separated by equals sign. A {@link Map} cant be used because of maven coords
     * containing a colon.
     */
    @Parameter(alias = "replaced-dependencies", required = true)
    private List<String> replacedDependencies = Lists.array();

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

    // initial-script-filename..........................................................................................

    private J2clPath initialScriptFilename() {
        return J2clPath.with(Paths.get(this.initialScriptFilename));
    }

    @Parameter(alias = "initial-script-filename",
            defaultValue = "${project.groupId}/${project.artifactId}.js",
            required = true)
    private String initialScriptFilename;

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

    // javaBootstrap.....................................................................................................

    private Predicate<J2clArtifactCoords> isJavacBootstrap() {
        return this.groupAndArtifactPredicate(this.javacBootstrap);
    }

    /**
     * The JRE dependency as maven coordinates. This is necessary as the JAVAC BOOTSTRAP jar file is a special case during building.
     */
    @Parameter(alias = "javac-bootstrap", required = true)
    private String javacBootstrap;

    // jre..............................................................................................................

    private Predicate<J2clArtifactCoords> isJre() {
        return this.groupAndArtifactPredicate(this.jre);
    }

    /**
     * The JRE dependency as maven coordinates. This is necessary as the JRE jar file is a special case during building.
     */
    @Parameter(alias = "jre-jar-file", required = true)
    private String jre;

    /**
     * Factory that creates a {@link Predicate} that returns true if the group and artifact id match.
     */
    private Predicate<J2clArtifactCoords> groupAndArtifactPredicate(final String groupAndArtifact) {
        final int colon = groupAndArtifact.indexOf(':');
        if (-1 == colon) {
            throw new IllegalArgumentException("Invalid coords, expected 2 components (groupId and artifactId): " + CharSequences.quoteAndEscape(groupAndArtifact));
        }

        CharSequences.failIfNullOrEmpty(groupAndArtifact.substring(0, colon), "group-id");
        CharSequences.failIfNullOrEmpty(groupAndArtifact.substring(colon + 1), "artifact-id");

        return new Predicate<>() {

            @Override
            public boolean test(final J2clArtifactCoords test) {
                return groupAndArtifact.equals(test.groupId() + ":" + test.artifactId());
            }

            @Override
            public String toString() {
                return groupAndArtifact;
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
                return groupId.equals(test.groupId()) && artifactId.equals(test.artifactId()) && version.equals(test.baseVersion());
            }

            @Override
            public String toString() {
                return coords;
            }
        };
    }
}
