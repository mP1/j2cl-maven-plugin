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
import walkingkooka.text.CaseSensitivity;
import walkingkooka.util.SystemProperty;

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
import java.util.concurrent.atomic.AtomicInteger;

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

            final J2clArtifact project = this.gatherDependencies(
                    logger,
                    context
            );

            this.prepareWatchBuild(project);

            context.prepareAndStart(
                    project,
                    logger
            );
            context.waitUntilCompletion();

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
                J2clPath.with(
                        this.buildOutputDirectory()
                ),
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

    private void waitAndBuild(final Path buildOutputDirectory,
                              final J2clArtifact project,
                              final TreeLogger logger,
                              final J2clMojoWatchMavenContext context) {
        context.fileEventRebuildPhase = true;

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
                logger.error(e.getMessage(), e);
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

    private static final boolean IS_MAC = CaseSensitivity.INSENSITIVE.contains(
            SystemProperty.OS_NAME.requiredPropertyValue(),
            "mac"
    );

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (final InterruptedException ignore) {
            // ignore
        }
    }

    private void watchServiceTakeAndPullLoop(final WatchService watchService,
                                             final J2clArtifact project,
                                             final TreeLogger logger,
                                             final J2clMojoWatchMavenContext context) throws InterruptedException {
        final AtomicInteger fileEventCounter = new AtomicInteger();

        for (; ; ) {
            final WatchKey key = watchService.take();
            if (null != key) {
                final List<WatchEvent<?>> events = key.pollEvents();
                if (!events.isEmpty()) {
                    final int fileEventCounterSnapshot = fileEventCounter.incrementAndGet();

                    logger.fileWatchEvents(events);

                    context.cancel(null);

                    context.submitTask(
                            () -> {
                                // wait a bit until the last file event arrives.
                                this.sleep();

                                // if $fileEventCounter hasnt changed, probably the last of multiple file events.
                                if (fileEventCounter.get() == fileEventCounterSnapshot) {
                                    this.build(
                                            project,
                                            logger,
                                            context
                                    );
                                }

                                return (Void) null;
                            }
                    );
                }
                key.reset();
            }
        }
    }

    private void build(final J2clArtifact project,
                       final TreeLogger logger,
                       final J2clMojoWatchMavenContext context) throws IOException {
        final Instant start = Instant.now();

        this.prepareWatchBuild(project);

        logger.info("Build");
        logger.indent();
        {
            try {
                context.prepareAndStart(
                        project,
                        logger
                );
            } catch (final Throwable cause) {
                logger.error("Build failed", cause);
            }
        }
        logger.outdent();

        logger.timeTaken(
                Duration.between(
                        start,
                        Instant.now()
                )
        );
    }

    private void prepareWatchBuild(final J2clArtifact project) throws IOException {
        // the cache directory will not have a hash and will have a trailing "-watch"
        final J2clPath output = project.setDirectory("watch")
                .directory();

        // empty the previous watch rebuild or create an empty dir
        if (output.exists().isPresent()) {
            output.removeAll();
        } else {
            output.createIfNecessary();
        }
    }
}
