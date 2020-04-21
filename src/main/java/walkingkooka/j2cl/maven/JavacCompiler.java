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
import walkingkooka.util.SystemProperty;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes javac using the inputs provided.
 */
final class JavacCompiler {

    static boolean execute(final List<J2clPath> bootstrap,
                           final Collection<J2clPath> classpath,
                           final List<J2clPath> newSourceFiles, // files being compiled
                           final J2clPath newClassFilesOutput,
                           final boolean runAnnotationProcessors,
                           final J2clLinePrinter logger) throws Exception {
        // try and add options in alpha order...
        final List<String> options = Lists.array();
        options.add("-bootclasspath");
        options.add(toClasspathStringList(bootstrap));
        options.add("-implicit:none");

        if(false == runAnnotationProcessors) {
            options.add("-proc:none");
        }

        final boolean success;
        {
            logger.printLine("Parameters");
            logger.indent();
            {
                logger.printIndented("Bootstrap", bootstrap);
                logger.printIndented("Classpath(s)", classpath);
                logger.printIndented("New java file(s)", newSourceFiles); // printLine full paths here might be mixed sources...
                logger.printIndented("Output", newClassFilesOutput);
                logger.printIndentedString("Option(s)", options);
            }
            logger.outdent();

            logger.printLine("Messages");
            logger.emptyLine();
            logger.indent();
            {
                final JavaCompiler compiler = javaCompiler();
                final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
                fileManager.setLocation(StandardLocation.SOURCE_PATH, Collections.emptyList()); // Location to search for existing source files.
                fileManager.setLocation(StandardLocation.CLASS_PATH, J2clPath.toFiles(classpath)); /// Location to search for user class files.
                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(newClassFilesOutput.file())); /// Location of new class files

                try(final Writer output = output(logger)) {
                    success = compiler.getTask(output,
                            fileManager,
                            null,
                            options,
                            null,
                            fileManager.getJavaFileObjectsFromFiles(J2clPath.toFiles(newSourceFiles)))
                            .call();
                    logger.printEndOfList();
                }
            }
            logger.outdent();
        }

        return success;
    }

    /**
     * Returns the {@link JavaCompiler} and includes a hacked attempt to locate the javacompiler for OSX or fails.
     */
    private static JavaCompiler javaCompiler() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (null == compiler) {
            throw new J2clException("JavaCompiler missing, require JDK not JRE.");
        }
        return compiler;
    }

    private static String toClasspathStringList(final List<J2clPath> entries) {
        return entries.stream()
                .map(J2clPath::toString)
                .collect(Collectors.joining(SystemProperty.JAVA_CLASS_PATH_SEPARATOR.requiredPropertyValue()));
    }

    /**
     * A {@link Writer} which will receive javac output and prints each line to the given {@link J2clLinePrinter}.
     */
    private static Writer output(final J2clLinePrinter logger) {
        return new Writer() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void write(final char[] text,
                              final int offset,
                              final int length) {
                logger.print(new String(text, offset, length));
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
