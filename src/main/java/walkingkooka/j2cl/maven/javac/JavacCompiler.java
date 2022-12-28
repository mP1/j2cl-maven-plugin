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

package walkingkooka.j2cl.maven.javac;

import walkingkooka.NeverError;
import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clException;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.util.SystemProperty;

import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Executes javac using the inputs provided.
 */
final class JavacCompiler {

    static boolean execute(final List<J2clPath> bootstrap,
                           final List<J2clPath> classpath,
                           final Set<J2clPath> newSourceFiles, // files being compiled
                           final J2clPath newClassFilesOutput,
                           final Set<String> javaCompilerArguments,
                           final boolean runAnnotationProcessors,
                           final TreeLogger logger) throws Exception {
        if (bootstrap.isEmpty()) {
            throw new IllegalArgumentException("bootstrap must not be empty");
        }
        if (classpath.isEmpty()) {
            throw new IllegalArgumentException("classpath must not be empty");
        }

        // try and add options in alpha order...
        final List<String> options = Lists.array();
        options.add("-bootclasspath");
        options.add(toClasspathStringList(bootstrap));
        options.add("-implicit:none");

        if(false == runAnnotationProcessors) {
            options.add("-proc:none");
        }

        // this assumes the arguments are well formed.
        options.addAll(javaCompilerArguments);

        final boolean success;
        {
            if (logger.isDebugEnabled()) {
                logger.line("Parameters");
                logger.indent();
                {
                    logger.paths("Bootstrap", bootstrap, TreeFormat.FLAT);
                    logger.paths("Classpath(s)", classpath, TreeFormat.FLAT);
                    logger.paths("New java file(s)", newSourceFiles, TreeFormat.TREE); // order should not be important so tree
                    logger.path("Output", newClassFilesOutput);
                    logger.strings("Option(s)", options);
                }
                logger.outdent();
            }

            logger.line("Messages");
            logger.emptyLine();
            logger.indent();
            {
                final JavaCompiler compiler = javaCompiler();
                final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
                fileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.emptyList()); // Location to search for existing source files.
                fileManager.setLocation(StandardLocation.CLASS_PATH, J2clPath.toFiles(classpath)); /// Location to search for user class files.
                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(newClassFilesOutput.file())); /// Location of new class files

                try (final Writer output = output(logger)) {
                    success = compiler.getTask(output,
                                    fileManager,
                                    diagnostic -> {
                                        final Diagnostic.Kind kind = diagnostic.getKind();

                                        Consumer<String> target = null;

                                        switch (kind) {
                                            case ERROR:
                                                target = (line) -> logger.error(line, null);
                                                break;
                                            case WARNING:
                                            case MANDATORY_WARNING:
                                                target = logger::line; // TODO should use WARN not INFO
                                                break;
                                            case NOTE:
                                            case OTHER:
                                                target = logger::line;
                                                break;
                                            default:
                                                NeverError.unhandledCase(
                                                        kind,
                                                        Diagnostic.Kind.values()
                                                );
                                        }

                                        // /Users/miroslav/repos-github/vertispan-connected-2/src/main/java/com/vertispan/draw/connected/client/FlowChartEntryPoint.java:85:64
                                        //java: not a statement

                                        Lists.of(
                                                diagnostic.getSource().getName() +
                                                        ":" +
                                                        diagnostic.getLineNumber() +
                                                        ":" +
                                                        diagnostic.getColumnNumber(),
                                                diagnostic.getMessage(Locale.getDefault())
                                        ).forEach(target);
                                    },
                                    options,
                                    null,
                                    fileManager.getJavaFileObjectsFromFiles(J2clPath.toFiles(newSourceFiles)))
                            .call();
                    logger.endOfList();
                }
            }
            logger.outdent();
        }

        return success;
    }

    /**
     * Returns the {@link JavaCompiler} and includes a hacked attempt to locate the javacompiler for OSX or fails.
     */
    private static JavaCompiler javaCompiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (null == compiler) {
            throw new J2clException("JavaCompiler missing, require JDK not JRE.");
        }
        return compiler;
    }

    private static String toClasspathStringList(final Collection<J2clPath> entries) {
        return entries.stream()
                .map(J2clPath::toString)
                .collect(Collectors.joining(SystemProperty.JAVA_CLASS_PATH_SEPARATOR.requiredPropertyValue()));
    }

    /**
     * A {@link Writer} which will receive javac output and prints each line to the given {@link TreeLogger}.
     */
    private static Writer output(final TreeLogger logger) {
        return new Writer() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void write(final char[] text,
                              final int offset,
                              final int length) {
                logger.log(
                        new String(text, offset, length)
                );
            }

            @Override
            public void flush() {
                logger.flush();
            }

            @Override
            public void close() {
                this.flush();
            }

            @Override
            public String toString() {
                return logger.toString();
            }
        };
    }
}
