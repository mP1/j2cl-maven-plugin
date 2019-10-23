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

import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.DependencyOptions;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

class ClosureCompiler {

    static boolean compile(final CompilationLevel compilationLevel,
                           final Map<String, String> defines,
                           final List<String> entryPoints,
                           final Set<String> externs,
                           final J2clPath initialScriptFilename,
                           final List<J2clPath> sources,
                           final J2clPath output,
                           final J2clLinePrinter logger) throws Exception {
        return compile0(compilationLevel,
                defines,
                entryPoints,
                sorted(externs),
                initialScriptFilename,
                sorted(sources),
                output,
                logger);
    }

    private static <T> SortedSet<T> sorted(final Collection<T> collection) {
        final SortedSet<T> sorted = Sets.sorted();
        sorted.addAll(collection);
        return sorted;
    }

    private static boolean compile0(final CompilationLevel compilationLevel,
                                    final Map<String, String> defines,
                                    final List<String> entryPoints,
                                    final SortedSet<String> externs,
                                    final J2clPath initialScriptFilename,
                                    final SortedSet<J2clPath> sources,
                                    final J2clPath output,
                                    final J2clLinePrinter logger) throws Exception {
        int fileCount = 0;
        logger.printLine(sources.size() + " Source(s)");
        logger.indent();
        {
            for (final J2clPath source : sources) {
                fileCount += printFiles(source.path(), logger);
            }
            logger.printEndOfList();
        }
        logger.outdent();

        final boolean success;
        if (0 == fileCount) {
            logger.printLine("No js files found, Closure compile aborted!");

            success = false;
        } else {
            final Map<String, Collection<String>> arguments;

            logger.printLine("Parameters");
            logger.indent();
            {
                final Path initialScriptFilenamePath = Paths.get(output.path().toString(), initialScriptFilename.toString());
                final Path parentOf = initialScriptFilenamePath.getParent();
                Files.createDirectories(parentOf);

                arguments = prepareCommandLineArguments(compilationLevel,
                        defines,
                        entryPoints,
                        externs,
                        sources,
                        initialScriptFilenamePath);

                logCommandLineArguments(arguments, logger);
            }
            logger.outdent();

            logger.printLine("Closure compiler");
            logger.indent();
            {
                final ByteArrayOutputStream compilerOutputBytes = new ByteArrayOutputStream();
                final String charset = Charset.defaultCharset().name();
                final Compiler compiler = new Compiler(new PrintStream(compilerOutputBytes,
                        true,
                        charset));
                final ClosureCompilerCommandLineRunner runner = new ClosureCompilerCommandLineRunner(compiler, argumentsToArray(arguments));
                runner.run();

                final int exitCode = runner.exitCode;

                logger.printLine("Exit code");
                logger.printIndentedLine("" + exitCode);

                logger.printLine("Messages");
                logger.indent();
                logger.emptyLine();
                logger.print(new String(compilerOutputBytes.toByteArray(), charset)); // the captured output will already have line endings.
                logger.outdent();

                // anything but zero means errors and is a FAIL.
                success = 0 == exitCode;
            }
            logger.outdent();
            logger.flush();
        }

        return success;
    }

    /**
     * Discovers all the js files under the given {@link Path}.
     */
    private static int printFiles(final Path root,
                                  final J2clLinePrinter logger) throws IOException {
        final Set<String> files = Sets.sorted();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes attrs) {
                if (CharSequences.endsWith(file.getFileName().toString(), ".js")) {
                    files.add(root.relativize(file).toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        logger.printLine(root.toString());
        logger.indent();
        {
            files.forEach(logger::printLine);
        }
        logger.outdent();

        return files.size();
    }

    /**
     * Builds a multi-value {@link Map} holding all individual argument tokens.
     */
    private static Map<String, Collection<String>> prepareCommandLineArguments(final CompilationLevel compilationLevel,
                                                                               final Map<String, String> defines,
                                                                               final List<String> entryPoints,
                                                                               final Set<String> externs,
                                                                               final Set<J2clPath> sourceRoots,
                                                                               final Path initialScriptFilenamePath) {
        final Map<String, Collection<String>> arguments = Maps.sorted();

        arguments.put("--compilation_level", Sets.of(compilationLevel.name()));

        {
            final Set<String> definesSet = Sets.sorted();
            if (compilationLevel == CompilationLevel.BUNDLE) {
                definesSet.add("goog.ENABLE_DEBUG_LOADER=false");
            }
            // TODO not sure about escaping....
            for (final Map.Entry<String, String> define : defines.entrySet()) {
                definesSet.add(define.getKey() + "=" + define.getValue());
            }
            arguments.put("--define", definesSet);
        }
        arguments.put("--dependency_mode", Sets.of(DependencyOptions.DependencyMode.PRUNE.name()));
        arguments.put("--entry_point", entryPoints);
        arguments.put("--externs", externs);

        {
            final Set<String> paths = Sets.sorted();
            for (final J2clPath source : sourceRoots) {
                paths.add(Paths.get(source.toString()) + "/**/*.js");
            }
            arguments.put("--js", paths);
        }

        arguments.put("--js_output_file", Sets.of(initialScriptFilenamePath.toString()));

        arguments.put("--language_out", Sets.of("ECMASCRIPT5"));

        return arguments;
    }

    private static void logCommandLineArguments(final Map<String, Collection<String>> arguments,
                                                final J2clLinePrinter logger) {
        logger.printLine("Command line arguments");
        logger.indent();
        {
            for (final Map.Entry<String, Collection<String>> keyAndValue : arguments.entrySet()) {
                logger.indent();
                {
                    final String key = keyAndValue.getKey();
                    logger.printLine(key);
                    logger.indent();

                    for (final String value : keyAndValue.getValue()) {
                        logger.printLine(value);
                    }
                    logger.outdent();
                }
                logger.outdent();
            }
            logger.printEndOfList();
        }
        logger.outdent();
    }

    /**
     * Takes a multi value map and converts it into an array made up of pairs holding the key and value.
     * Parameters with multiple values will have multiple entries in the array.
     */
    private static String[] argumentsToArray(final Map<String, Collection<String>> arguments) {
        final List<String> list = Lists.array();

        for (final Map.Entry<String, Collection<String>> keyAndValues : arguments.entrySet()) {
            final String key = keyAndValues.getKey();
            for (final String value : keyAndValues.getValue()) {
                list.add(key);
                list.add(value);
            }
        }

        return list.toArray(new String[list.size()]);
    }

    /**
     * Used to execute the Closure compiler.
     */
    static class ClosureCompilerCommandLineRunner extends CommandLineRunner {

        private int exitCode;

        ClosureCompilerCommandLineRunner(final Compiler compiler,
                                         final String[] args) {
            super(args);
            this.compiler = compiler;
            setExitCodeReceiver(exitCode -> {
                //noinspection ConstantConditions
                this.exitCode = exitCode;
                return null;
            });
        }

        @Override
        protected Compiler createCompiler() {
            return compiler;
        }

        private final Compiler compiler;
    }
}
