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
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.closure.ClosureFormattingOption;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Base class that captures maven plugin parameters and methods common to both build and test.
 */
abstract class J2clMojoBuildTest extends J2clMojo {

    J2clMojoBuildTest() {
        super();
    }

    // MAVEN............................................................................................................

    // classpathScope...................................................................................................

    final J2clClasspathScope classpathScope() {
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
    final J2clPath output() {
        return J2clPath.with(this.output.toPath());
    }

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}",
            required = true)
    private File output;


    // ignoredDependencies................................................................................................

    final List<J2clArtifactCoords> ignoredDependencies() {
        return parseList(this.ignoredDependencies);
    }

    /**
     * List of artifacts that will not be processed at all. When required on the java classpath or source,
     * the archive file will be added.
     */
    @Parameter(alias = "ignored-dependencies", required = true)
    private List<String> ignoredDependencies;

    // javascriptSourceRequired........................................................................................

    final List<J2clArtifactCoords> javascriptSourceRequired() {
        return parseList(this.javascriptSourceRequired);
    }

    /**
     * List of artifacts required when javascript sources are passed as arguments.
     */
    @Parameter(alias = "javascript-source-required", required = true)
    private List<String> javascriptSourceRequired;


    private static List<J2clArtifactCoords> parseList(final List<String> coords) {
        return coords.stream()
                .map(J2clArtifactCoords::parse)
                .collect(Collectors.toList());
    }

    // DEPENDENCIES.....................................................................................................

    /**
     * Gathers the all the dependencies.
     */
    final J2clDependency gatherDependencies(final TreeLogger logger,
                                            final J2clMavenContext context) {
        return J2clDependency.gather(
                this.mavenProject(),
                logger,
                context
        );
    }

    // JAVA.............................................................................................................

    // classpathRequired................................................................................................

    final List<J2clArtifactCoords> classpathRequired() {
        return parseList(this.classpathRequired);
    }

    /**
     * List of artifacts required on java classpaths.
     */
    @Parameter(alias = "classpath-required", required = true)
    private List<String> classpathRequired;

    // CLOSURE..........................................................................................................

    // compilationLevel.................................................................................................

    final CompilationLevel compilationLevel() {
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

    final Map<String, String> defines() {
        return this.defines;
    }

    @Parameter(required = true)
    private final Map<String, String> defines = new HashMap<>();

    // externs..........................................................................................................

    /**
     * Externs sorted alphabetically, aides pretty printing.
     */
    final SortedSet<String> externs() {
        return new java.util.TreeSet<>(this.externs);
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(required = true)
    private final List<String> externs = new ArrayList<>();


    // formatting..........................................................................................................

    /**
     * The formatting options if any are set.
     */
    final Set<ClosureFormattingOption> formatting() {
        return this.formatting.stream()
                .map(String::trim)
                .map(ClosureFormattingOption::fromCommandLine)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ClosureFormattingOption.class)));
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(required = true)
    private final List<String> formatting = new ArrayList<>();

    // javaCompilerArguments.............................................................................................

    /**
     * The javaCompilerArguments options if any are set. The original order is lost and further processing receives them as a {@link SortedSet}.
     */
    final Set<String> javaCompilerArguments() {
        return this.javaCompilerArguments.stream()
                .map(String::trim)
                .collect(Collectors.toCollection(Sets::sorted));
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter(alias = "java-compiler-arguments", required = true)
    private final List<String> javaCompilerArguments = new ArrayList<>();
    
    // language-out.....................................................................................................

    final LanguageMode languageOut() {
        return this.languageOut;
    }

    @Parameter(alias = "language-out",
            required = true)
    private LanguageMode languageOut;

    // source-maps......................................................................................................

    final Optional<String> sourceMaps() {
        final String sourceMaps = this.sourceMaps;
        return Optional.ofNullable(
                CharSequences.isNullOrEmpty(sourceMaps) ?
                        null :
                        sourceMaps);
    }

    @Parameter(alias = "source-maps")
    private String sourceMaps;

    // project..........................................................................................................

    final MavenProject mavenProject() {
        return this.mavenProject;
    }

    @Parameter(defaultValue = "${project}",
            readonly = true,
            required = true)
    private MavenProject mavenProject;

    // threadPool.......................................................................................................

    final ExecutorService executor() {
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

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Factory that creates a {@link J2clMavenMiddleware}
     */
    final J2clMavenMiddleware mavenMiddleware() {
        if (null == this.mavenMiddleware) {
            this.mavenMiddleware = J2clMavenMiddleware.of(
                    this.artifactHandlerManager,
                    this.mavenSession,
                    this.projectBuilder,
                    this.mavenProject.getRemoteArtifactRepositories(),
                    this.repositories,
                    this.repositorySession,
                    this.repositorySystem
            );
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
