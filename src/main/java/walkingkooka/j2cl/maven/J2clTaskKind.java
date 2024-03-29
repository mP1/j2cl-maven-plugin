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

import walkingkooka.Cast;
import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.log.MavenLogger;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.LineEnding;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.Printers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The individual tasks that are executed in series to complete the process of building.
 */
public enum J2clTaskKind {
    /**
     * Computes the hash for the given {@link J2clArtifact} including its dependencies.
     */
    HASH {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.hash();
        }
    },

    /**
     * For archives (dependencies) unpack the accompanying sources.
     */
    UNPACK {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.unpack();
        }
    },

    /**
     * Calls javac on the unpack directory along with its dependencies on the classpath.
     */
    JAVAC_ANNOTATION_PROCESSORS_ENABLED {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.compileJavaSource();
        }
    },

    /**
     * Calls the @GwtIncompatible stripper on /compile saving into /gwt-incompatible-strip
     */
    GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.gwtIncompatStrip();
        }
    },

    /**
     * Compiles /gwt-incompatible-strip along with dependencies on the classpath into /gwt-incompatible-strip-compiled
     */
    JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.compileGwtIncompatStripped();
        }
    },

    /**
     * Attempts to find files called "j2cl-maven-plugin-shade.txt" in the root of the dependency files and uses that to
     * shade java source files.
     */
    SHADE_JAVA_SOURCE {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.shadeJavaSource();
        }
    },

    /**
     * Attempts to find files called "j2cl-maven-plugin-shade.txt" in the root of the dependency files and if found
     * shares matching files.
     */
    SHADE_CLASS_FILES {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.shadeClassFiles();
        }

    },

    /**
     * Calls the transpiler on the output of previous tasks.
     */
    TRANSPILE_JAVA_TO_JAVASCRIPT {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.transpiler();
        }
    },
    /**
     * Calls the closure compiler on the /transpiler along with other "files" into /closure-compiled.
     */
    CLOSURE_COMPILE {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.closure();
        }
    },
    /**
     * Assembles the output and copies files to that place.
     */
    OUTPUT_ASSEMBLE {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return J2clTasks.outputAssembler();
        }
    },
    /**
     * Uses webdriver to execute a junit test.
     */
    JUNIT_TESTS {
        @Override
        J2clTask<? super J2clMavenContext> task() {
            return Cast.to(
                    J2clTasks.unitTests()
            );
        }
    };

    // work methods.....................................................................................................

    /**
     * A {@link Callable} that creates a logger that is saved to a log file when the task is successful (completes without
     * throwing) or logs as errors to the output anything printed during execution.
     */
    final <C extends J2clMavenContext> Optional<J2clTaskKind> execute(final J2clArtifact artifact,
                                                                      final TreeLogger parentLogger,
                                                                      final C context) throws Exception {
        final Instant start = Instant.now();

        final List<CharSequence> lines = Lists.array(); // these lines will be written to a log file.
        final String prefix = artifact.coords() + "-" + this;

        final TreeLogger logger = parentLogger.childTreeLogger(
                lines::add,
                (line, thrown) -> {
                    lines.add(line);
                    if (null != thrown) {
                        final Printer printer = Printers.sink(LineEnding.SYSTEM)
                                .printedLine(
                                        (final CharSequence l,
                                         final LineEnding lineEnding,
                                         final Printer p) -> lines.add(l)
                                );
                        thrown.printStackTrace(printer.asPrintWriter());
                    }
                }
        );

        try {
            J2clTaskResult result;

            logger.line(prefix);
            logger.indent();
            {
                result = this.task()
                        .execute(
                                artifact,
                                this,
                                context,
                                logger
                        );

                final J2clTaskDirectory directory = artifact.taskDirectory(this);

                directory.writeLog(
                        lines,
                        timeTaken(start),
                        logger
                );

                result.path(directory)
                        .createIfNecessary();
            }
            return result.next(
                    artifact,
                    this,
                    context
            );
        } catch (final Exception cause) {
            logger.error("Failed to execute " + prefix + " message: " + cause.getMessage(), cause);
            logger.flush();

            final J2clTaskDirectory directory = artifact.taskDirectory(this);
            directory.failed()
                    .createIfNecessary();

            if (directory.path().exists().isPresent()) {
                directory.writeLog(
                        lines,
                        timeTaken(start),
                        logger
                );
            } else {
                // HASH task probably failed so create a unique file and write it to the base directory.
                final Path base = Paths.get(
                        context.cache()
                                .path()
                                .toString(),
                        artifact.coords() +
                                "-" +
                                DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                );

                logger.error("Log file", null);
                logger.error(MavenLogger.INDENTATION + base.toString(), null);
                logger.flush();

                Files.write(base, lines);
            }

            throw cause;
        }
    }

    private Duration timeTaken(final Instant start) {
        return Duration.between(
                start,
                Instant.now()
        );
    }

    final String directoryName(final int number) {
        return number +
                "-" +
                this.name()
                        .toLowerCase()
                        .replace('_', '-');
    }

    abstract J2clTask<? super J2clMavenContext> task();
}
