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
 * The individual steps that are executed in series to complete the process or building.
 */
enum J2clStep {
    /**
     * Computes the hash for the given {@link J2clDependency} including its dependencies.
     */
    HASH {
        @Override
        String directoryName() {
            return "0-hash";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.HASH;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(UNPACK);
        }
    },

    /**
     * For archives (dependencies) unpack the accompanying sources.
     */
    UNPACK {
        @Override
        String directoryName() {
            return "1-unpack";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.UNPACK;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(JAVAC_COMPILE);
        }
    },

    /**
     * Calls javac on the unpack directory along with its dependencies on the classpath into /compile
     */
    JAVAC_COMPILE {
        @Override
        String directoryName() {
            return "2-javac-enabled-annotation-processors-source";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.COMPILE_SOURCE;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(GWT_INCOMPATIBLE_STRIP);
        }
    },

    /**
     * Calls the @GwtIncompatible stripper on /compile saving into /gwt-incompatible-strip
     */
    GWT_INCOMPATIBLE_STRIP {
        @Override
        String directoryName() {
            return "3-gwt-incompatible-stripped-source";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.STRIP_GWT_INCOMPAT;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(COMPILE_GWT_INCOMPATIBLE_STRIPPED);
        }
    },

    /**
     * Compiles /gwt-incompatible-strip along with dependencies on the classpath into /gwt-incompatible-strip-compiled
     */
    COMPILE_GWT_INCOMPATIBLE_STRIPPED {
        @Override
        String directoryName() {
            return "4-javac-gwt-incompatible-stripped-source";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.COMPILE_STRIP_GWT_INCOMPAT;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(SHADE_JAVA_SOURCE);
        }
    },

    /**
     * Attempts to find files called "j2cl-maven-plugin-shade.txt" in the root of the dependency files and uses that to
     * shade java source files.
     */
    SHADE_JAVA_SOURCE {
        @Override
        String directoryName() {
            return "5-shaded-java-source";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.SHADE_JAVA_SOURCE;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(SHADE_CLASS_FILES);
        }
    },

    /**
     * Attempts to find files called "j2cl-maven-plugin-shade.txt" in the root of the dependency files and uses that to
     * shade class files.
     */
    SHADE_CLASS_FILES {
        @Override
        String directoryName() {
            return "6-shaded-class-files";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.SHADE_CLASS_FILE;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(TRANSPILE);
        }
    },

    /**
     * Calls the transpiler on the output of previous steps.
     */
    TRANSPILE {
        @Override
        String directoryName() {
            return "7-transpiled-java-to-javascript";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.TRANSPILER;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(CLOSURE_COMPILER);
        }
    },
    /**
     * Calls the closure compiler on the /transpiler along with other "files" into /closure-compiled.
     */
    CLOSURE_COMPILER {
        @Override
        String directoryName() {
            return "8-closure-compile";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.CLOSURE;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.of(J2clClasspathScope.TEST == request.scope() ?
                    JUNIT_WEBDRIVER_TESTS :
                    OUTPUT_ASSEMBLER);
        }
    },
    /**
     * Assembles the output and copies files to the place.
     */
    OUTPUT_ASSEMBLER {
        @Override
        String directoryName() {
            return "9-output-assembler";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.OUTPUT_ASSEMBLER;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.empty();
        }
    },
    /**
     * Uses webdriver to execute a junit test.
     */
    JUNIT_WEBDRIVER_TESTS {
        @Override
        String directoryName() {
            return "9-junit-tests";
        }

        @Override
        J2clStepWorker execute1() {
            return J2clStepWorker.JUNIT_WEBDRIVER_TESTS;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clStep> next(final J2clRequest request) {
            return Optional.empty();
        }
    };

    public final static J2clStep FIRST = HASH;

    // step directory naming............................................................................................

    /**
     * Returns the actual sub directory name on disk. The directory will hold all the output files created by this step.
     */
    abstract String directoryName();

    // work methods.....................................................................................................

    /**
     * A {@link Callable} that creates a logger that is saved to a log file when the task is successful (completes without
     * throwing) or logs as errors to the output anything printed during execution.
     */
    final Optional<J2clStep> execute(final J2clDependency artifact) throws Exception {
        final Instant start = Instant.now();

        final J2clRequest request = artifact.request();
        final J2clLogger j2clLogger = request.logger();
        final List<CharSequence> lines = Lists.array(); // these lines will be written to a log file.
        final String prefix = artifact.coords() + "-" + this;

        final PrintedLineHandler lineHandler = (line, eol, p) -> {
            p.print(line);
            p.flush();
            lines.add(line);
        };

        // TODO would be nice to detect log level of Maven logger
        final J2clLinePrinter logger = J2clLinePrinter.with(j2clLogger.printer(j2clLogger::info).printedLine(lineHandler),
                j2clLogger.printer(j2clLogger::debug).printedLine(lineHandler));
        try {
            final J2clStepResult result;
            if (artifact.isDependency() && this.skipIfDependency()) {
                result = J2clStepResult.SUCCESS;
            } else {
                logger.printLine(prefix);
                logger.indent();

                result = this.execute1()
                        .execute(artifact,
                                this,
                                logger);
                final J2clStepDirectory directory = artifact.step(this);
                directory.writeLog(lines, logger);

                result.path(directory).createIfNecessary();

                final Duration timeTaken = Duration.between(
                        start,
                        Instant.now()
                );

                logger.printLine("Time taken");
                logger.printIndentedLine(timeTaken.getSeconds() + "." + timeTaken.getNano() + " seconds");
                logger.emptyLine();
                logger.flush();

                result.reportIfFailure(artifact, this);
            }
            return result.next(this.next(request));
        } catch (final Exception cause) {
            logger.flush();

            j2clLogger.error("Failed to execute " + prefix + " message: " + cause.getMessage(), cause);
            lines.forEach(l -> j2clLogger.error(prefix + " " + l));

            // capture stack trace into $lines
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final String charset = Charset.defaultCharset().name();
            cause.printStackTrace(new PrintStream(bytes, true, charset));
            logger.emptyLine();
            logger.print(new String(bytes.toByteArray(), charset));

            final J2clStepDirectory directory = artifact.step(this);
            directory.failed()
                    .createIfNecessary();

            if (directory.path().exists().isPresent()) {
                directory.writeLog(lines, logger);
            } else {
                // HASH step probably failed so create a unique file and write it to the base directory.
                final Path base = Paths.get(request.base().path().toString(), artifact.coords() + "-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));

                j2clLogger.error("Log file");
                j2clLogger.error(J2clLogger.INDENTATION + base.toString());

                Files.write(base, lines);
            }
            artifact.request().cancel(cause);

            throw cause;
        }
    }

    /**
     * Returns the sub class of {@link J2clStepWorker} and then calls {@link J2clStepWorker#execute(J2clDependency, J2clStep, J2clLinePrinter)
     */
    abstract J2clStepWorker execute1();

    // skipIfJre........................................................................................................

    /**
     * Some steps should not be attemped by dependencies.
     */
    abstract boolean skipIfDependency();

    // next.............................................................................................................

    /**
     * Returns the next step if one is present.
     */
    abstract Optional<J2clStep> next(final J2clRequest request);
}
