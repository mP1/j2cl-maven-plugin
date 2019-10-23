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

import afu.org.checkerframework.checker.oigj.qual.O;
import com.google.javascript.jscomp.CompilationLevel;
import org.apache.maven.artifact.Artifact;
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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

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
            request.execute(J2clDependency.gather(this.project,
                    J2clSourcesKind.SRC,
                    request));
        } catch (final Throwable e) {
            throw new MojoExecutionException("Failed to build project, check logs above", e);
        }
    }

    private J2clBuildRequest request() {
        return J2clBuildRequest.with(this.classpathScope(),
                this.compilationLevel(),
                this.defines(),
                this.externs(),
                this.entryPoints(),
                this.initialScriptFilename(),
                this.cache(),
                this.isJavacBootstrap(),
                this.isJre(),
                this.output(),
                this.mavenMiddleware(),
                this.executor(),
                this.logger());
    }

    // classpathScope...................................................................................................

    private J2clClasspathScope classpathScope() {
        //return
        J2clClasspathScope.commandLineOption(Artifact.SCOPE_COMPILE_PLUS_RUNTIME); // compile+runtime
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
        return CompilationLevel.fromString(this.compilationLevel);
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
        return J2clPath.with(this.initialScriptFilename.toPath());
    }

    @Parameter(alias = "initial-script-filename",
            defaultValue = "${project.artifactId}/${project.artifactId}.js",
            required = true)
    private File initialScriptFilename;

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
        return this.artifactDependency(this.javacBootstrap);
    }

    /**
     * The JRE dependency as maven coordinates. This is necessary as the JAVAC BOOTSTRAP jar file is a special case during building.
     */
    @Parameter(alias = "javac-bootstrap", required = true)
    private String javacBootstrap;

    // jre..............................................................................................................

    private Predicate<J2clArtifactCoords> isJre() {
        return this.artifactDependency(this.jre);
    }

    /**
     * Only matches using the group-id and artifact-id the version is ignored.
     */
    private Predicate<J2clArtifactCoords> artifactDependency(final String groupAndArtifact) {
        return new Predicate<> () {

            @Override
            public boolean test(final J2clArtifactCoords coords) {
                System.out.println("->" + groupAndArtifact + "=" + coords.groupId() + ":" + coords.artifactId() + "===> " +
                        (groupAndArtifact.equals(coords.groupId() + ":" + coords.artifactId())));
                return groupAndArtifact.equals(coords.groupId() + ":" + coords.artifactId());
            }

            @Override
            public String toString() {
                return groupAndArtifact;
            }
        };
    }

    /**
     * The JRE dependency as maven coordinates. This is necessary as the JRE jar file is a special case during building.
     */
    @Parameter(alias = "jre-jar-file", required = true)
    private String jre;
}
