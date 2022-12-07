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


import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The watch task watches the output directory for class file changes. For each change it then analyzes the class file
 * computing changes to any related classes then completes the build process.
 */
@Mojo(name = "watch", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public final class J2clMojoWatch extends J2clMojoBuildWatch {

    /**
     * This is the directory where the IDE is building and placing class files. Changes to class files in this directory
     * will result in a rebuilding and generation
     */
    @Parameter(alias = "build-output-directory",
            defaultValue = "${project.build.directory}/classes",
            required = true)
    private String buildOutputDirectory;

    private Path buildOutputDirectory() {
        return Paths.get(this.buildOutputDirectory);
    }

    /**
     * Watches the output directory where the IDE places class files.
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            final J2clMojoWatchMavenContext context = this.context();
            final TreeLogger logger = context.mavenLogger()
                    .treeLogger();

            final Path buildOutputDirectory = this.buildOutputDirectory();
            logger.path(
                    "Watching",
                    J2clPath.with(buildOutputDirectory)
            );

            final J2clDependency project = this.gatherDependencies(
                    logger,
                    context
            );
            context.execute(
                    project,
                    logger
            );

            this.waitAndBuild(
                    buildOutputDirectory,
                    project,
                    logger,
                    context
            );
        } catch (final Throwable e) {
            throw new MojoExecutionException("Failed to build project, check logs above", e);
        }
    }

    /**
     * The {@link J2clMavenContext} accompanying the build.
     */
    private J2clMojoWatchMavenContext context() {
        return J2clMojoWatchMavenContext.with(
                this.cache(),
                this.output(),
                this.classpathScope(),
                this.classpathRequired(),
                this.ignoredDependencies(),
                this.javascriptSourceRequired(),
                this.compilationLevel(),
                this.defines(),
                this.externs(),
                this.entryPoints(),
                this.formatting(),
                this.initialScriptFilename(),
                this.javaCompilerArguments(),
                this.languageOut(),
                this.sourceMaps(),
                this.mavenMiddleware(),
                this.threadPoolSize(),
                this.logger()
        );
    }

    private void waitAndBuild(final Path buildOutputDirectory, final J2clDependency project,
                              final TreeLogger logger,
                              final J2clMojoWatchMavenContext context) {
        for (; ; ) {
            try (final WatchService watchService = watchService(buildOutputDirectory)) {
                new WatchablePath(buildOutputDirectory).register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.OVERFLOW
                );

                this.watchServiceTakeAndPullLoop(
                        watchService,
                        project,
                        logger,
                        context
                );

            } catch (final Throwable e) {
                // ignore and try again.
            }

            this.sleep();
        }
    }

    private WatchService watchService(final Path watching) throws IOException {
        return
                IS_MAC ?
                        new MacOSXListeningWatchService() :
                        watching.getFileSystem().newWatchService();
    }

    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (final InterruptedException ignore) {
            // ignore
        }
    }

    private void watchServiceTakeAndPullLoop(final WatchService watchService,
                                             final J2clDependency project,
                                             final TreeLogger logger,
                                             final J2clMojoWatchMavenContext context) throws InterruptedException, MojoExecutionException {
        for (; ; ) {
            final WatchKey key = watchService.take();
            if (null != key) {
                final List<WatchEvent<?>> events = key.pollEvents();
                if (!events.isEmpty()) {
                    build(
                            events,
                            project,
                            logger,
                            context
                    );
                }
                key.reset();
            }
        }
    }

    private void build(final List<WatchEvent<?>> events,
                       final J2clDependency project,
                       final TreeLogger logger,
                       final J2clMojoWatchMavenContext context) {
        final Instant start = Instant.now();

        logger.indent();
        {
            logger.info("File event(s)");
            logger.indent();
            {
                for (final WatchEvent<?> event : events) {
                    logger.line(
                            event.kind() + " " + event.context()
                    );
                }
            }
            logger.outdent();

            logger.info("Build");
            logger.indent();
            {
                try {
                    context.execute(
                            project,
                            logger
                    );
                } catch (final Throwable cause) {
                    logger.error("Build failed", cause);
                }
            }
            logger.outdent();

            final Duration timeTaken = Duration.between(
                    start,
                    Instant.now()
            );

            logger.line("Time taken");
            logger.indentedLine(
                    TreeLogger.prettyTimeTaken(timeTaken)
            );
            logger.emptyLine();
            logger.flush();
        }
        logger.outdent();
    }
}
