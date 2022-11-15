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

import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.log.MavenLogger;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.printer.PrintedLineHandler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
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
 * The individual steps that are executed in series to complete the process of building.
 */
enum J2clStep {
    /**
     * Computes the hash for the given {@link J2clDependency} including its dependencies.
     */
    HASH {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.HASH;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(UNPACK);
        }
    },

    /**
     * For archives (dependencies) unpack the accompanying sources.
     */
    UNPACK {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.UNPACK;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(JAVAC_COMPILE);
        }
    },

    /**
     * Calls javac on the unpack directory along with its dependencies on the classpath.
     */
    JAVAC_COMPILE {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.COMPILE_SOURCE;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE);
        }
    },

    /**
     * Calls the @GwtIncompatible stripper on /compile saving into /gwt-incompatible-strip
     */
    GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.STRIP_GWT_INCOMPAT;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE);
        }
    },

    /**
     * Compiles /gwt-incompatible-strip along with dependencies on the classpath into /gwt-incompatible-strip-compiled
     */
    JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.COMPILE_STRIP_GWT_INCOMPAT;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(SHADE_JAVA_SOURCE);
        }
    },

    /**
     * Attempts to find files called "j2cl-maven-plugin-shade.txt" in the root of the dependency files and uses that to
     * shade java source files.
     */
    SHADE_JAVA_SOURCE {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.SHADE_JAVA_SOURCE;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(SHADE_CLASS_FILES);
        }
    },

    /**
     * Attempts to find files called "j2cl-maven-plugin-shade.txt" in the root of the dependency files and if found
     * shares matching files.
     */
    SHADE_CLASS_FILES {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.SHADE_CLASS_FILE;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(TRANSPILE_JAVA_TO_JAVASCRIPT);
        }
    },

    /**
     * Calls the transpiler on the output of previous steps.
     */
    TRANSPILE_JAVA_TO_JAVASCRIPT {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.TRANSPILER;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(CLOSURE_COMPILE);
        }
    },
    /**
     * Calls the closure compiler on the /transpiler along with other "files" into /closure-compiled.
     */
    CLOSURE_COMPILE {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.CLOSURE;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.of(
                    J2clClasspathScope.TEST == context.scope() ?
                            JUNIT_TESTS :
                            OUTPUT_ASSEMBLE
            );
        }
    },
    /**
     * Assembles the output and copies files to that place.
     */
    OUTPUT_ASSEMBLE {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.OUTPUT_ASSEMBLER;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.empty();
        }
    },
    /**
     * Uses webdriver to execute a junit test.
     */
    JUNIT_TESTS {
        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.JUNIT_WEBDRIVER_TESTS;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clStep> next(final J2clMavenContext context) {
            return Optional.empty();
        }
    };

    public final static J2clStep FIRST = HASH;

    // work methods.....................................................................................................

    /**
     * A {@link Callable} that creates a logger that is saved to a log file when the task is successful (completes without
     * throwing) or logs as errors to the output anything printed during execution.
     */
    final Optional<J2clStep> execute(final J2clDependency artifact) throws Exception {
        final Instant start = Instant.now();

        final J2clMavenContext context = artifact.context();
        final MavenLogger mavenLogger = context.mavenLogger();
        final List<CharSequence> lines = Lists.array(); // these lines will be written to a log file.
        final String prefix = artifact.coords() + "-" + this;

        final PrintedLineHandler lineHandler = (line, eol, p) -> {
            p.print(line);
            p.flush();
            lines.add(line);
        };

        final TreeLogger output = mavenLogger.output(
                lineHandler,
                lineHandler
        );

        try {
            final J2clStepResult result;
            if (artifact.isDependency() && this.skipIfDependency()) {
                result = J2clStepResult.SUCCESS;
            } else {
                output.line(prefix);
                output.indent();

                result = this.execute1()
                        .execute(artifact,
                                this,
                                output);

                final J2clStepDirectory directory = artifact.step(this);

                directory.writeLog(
                        lines,
                        timeTaken(start),
                        output
                );

                result.path(directory).createIfNecessary();

                result.reportIfFailure(artifact, this);
            }
            return result.next(this.next(context));
        } catch (final Exception cause) {
            output.flush();

            mavenLogger.error("Failed to execute " + prefix + " message: " + cause.getMessage(), cause);
            lines.forEach(l -> mavenLogger.error(prefix + " " + l));

            // capture stack trace into $lines
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final String charset = Charset.defaultCharset().name();
            cause.printStackTrace(new PrintStream(bytes, true, charset));
            output.emptyLine();
            output.log(
                    new String(
                            bytes.toByteArray(),
                            charset
                    )
            );

            final J2clStepDirectory directory = artifact.step(this);
            directory.failed()
                    .createIfNecessary();

            if (directory.path().exists().isPresent()) {
                directory.writeLog(
                        lines,
                        timeTaken(start),
                        output
                );
            } else {
                // HASH step probably failed so create a unique file and write it to the base directory.
                final Path base = Paths.get(
                        context.base()
                                .path()
                                .toString(),
                        artifact.coords() +
                                "-" +
                                DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                );

                mavenLogger.error("Log file");
                mavenLogger.error(MavenLogger.INDENTATION + base.toString());

                Files.write(base, lines);
            }
            artifact.context().cancel(cause);

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

    /**
     * Returns the sub-class of {@link J2clStepWorker} and then calls {@link J2clStepWorker#execute(J2clDependency, J2clStep, TreeLogger)
     */
    abstract J2clStepWorker execute1();

    // skipIfJre........................................................................................................

    /**
     * Some steps should not be attempted by dependencies.
     */
    abstract boolean skipIfDependency();

    // next.............................................................................................................

    /**
     * Returns the next step if one is present.
     */
    abstract Optional<J2clStep> next(final J2clMavenContext context);
}
