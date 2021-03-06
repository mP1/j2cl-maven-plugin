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
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DependencyOptions;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.LineEnding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class ClosureCompiler {

    static boolean compile(final CompilationLevel compilationLevel,
                           final Map<String, String> defines,
                           final List<String> entryPoints,
                           final Set<String> externs,
                           final Set<ClosureFormattingOption> formatting,
                           final LanguageMode languageOut,
                           final boolean exportTestFunctions,
                           final Optional<String> sourceMaps,
                           final Set<J2clPath> sources,
                           final J2clPath output,
                           final String initialScriptFilename,
                           final J2clLinePrinter logger) throws Exception {
        return compile0(compilationLevel,
                defines,
                entryPoints,
                new TreeSet<>(externs),
                formatting,
                languageOut,
                exportTestFunctions,
                sourceMaps,
                sources,
                output,
                initialScriptFilename,
                logger);
    }

    private static boolean compile0(final CompilationLevel compilationLevel,
                                    final Map<String, String> defines,
                                    final List<String> entryPoints,
                                    final SortedSet<String> externs,
                                    final Set<ClosureFormattingOption> formatting,
                                    final LanguageMode languageOut,
                                    final boolean exportTestFunctions,
                                    final Optional<String> sourceMaps,
                                    final Set<J2clPath> sources,
                                    final J2clPath output,
                                    final String initialScriptFilename,
                                    final J2clLinePrinter logger) throws Exception {
        int fileCount = 0;

        final J2clPath unitedSourceRoot = sourceMaps
                .map(s -> output.append(s))
                .orElse(output.parent().append("sources"));

        logger.printLine(sources.size() + " Source(s)");
        logger.indent();
        {
            for (final J2clPath sourceRoot : sources) {
                logger.printLine(sourceRoot.toString());
                logger.indent();
                {
                    final Collection<J2clPath> copied;
                    if (sourceRoot.isFile()) {
                        copied = sourceRoot.extractArchiveFiles(J2clPath.WITHOUT_META_INF,
                                unitedSourceRoot,
                                logger);
                    } else {
                        // if unpack/output dont want to copy java source.
                        final Predicate<Path> filter = sourceRoot.isUnpackOutput() ?
                                J2clPath.ALL_FILES_EXCEPT_JAVA :
                                J2clPath.ALL_FILES;

                        copied = unitedSourceRoot.copyFiles(sourceRoot,
                                sourceRoot.gatherFiles(filter),
                                J2clPath.COPY_FILE_CONTENT_VERBATIM,
                                logger);
                    }
                    fileCount += copied.size();
                }
                logger.outdent();
            }
            logger.printEndOfList();
        }
        logger.outdent();

        final boolean success;
        if (0 == fileCount) {
            logger.printLine("No js files found, Closure compile aborted!");

            success = false;
        } else {
            logger.printIndented("Output", output);

            final J2clPath initialScriptFilenamePath = output.append(initialScriptFilename);

            final Map<String, Collection<String>> arguments = prepareArguments(compilationLevel,
                    defines,
                    entryPoints,
                    externs,
                    formatting.stream().map(ClosureFormattingOption::name).collect(Collectors.toCollection(Sets::sorted)),
                    languageOut,
                    sourceMaps,
                    unitedSourceRoot,
                    initialScriptFilenamePath,
                    logger);

            logger.printLine("Closure compiler");
            logger.indent();
            {
                final String charset = Charset.defaultCharset().name();

                try(final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream()) {
                    final PrintStream outputPrintStream = new PrintStream(outputBytes,
                            true,
                            charset);

                    final Compiler compiler = new Compiler(outputPrintStream);

                    final ClosureCompilerCommandLineRunner runner = new ClosureCompilerCommandLineRunner(compiler,
                            argumentsToArray(arguments),
                            exportTestFunctions,
                            outputPrintStream,
                            outputPrintStream);
                    if (!runner.shouldRunCompiler()) {
                        throw new IllegalStateException("Closure Compiler setup has error(s), check recently logged messages");
                    }
                    runner.run();

                    final int exitCode = runner.exitCode;

                    logger.printLine("Exit code");
                    logger.printIndentedLine("" + exitCode);

                    logger.printLine("Messages");
                    logger.indent();
                    {
                        logger.lineStart(); // https://github.com/mP1/j2cl-maven-plugin/issues/254
                        logger.print(removeEmptyLines(new String(outputBytes.toByteArray(), charset))); // the captured output will already have line endings.
                    }
                    logger.outdent();

                    // anything but zero means errors and initial file must also exist and is a FAIL.
                    success = 0 == exitCode && initialScriptFilenamePath.exists().isPresent();
                }
            }
            logger.outdent();
            logger.flush();
        }

        return success;
    }

    private static Map<String, Collection<String>> prepareArguments(final CompilationLevel compilationLevel,
                                                                    final Map<String, String> defines,
                                                                    final List<String> entryPoints,
                                                                    final SortedSet<String> externs,
                                                                    final Set<String> formatting,
                                                                    final LanguageMode languageOut,
                                                                    final Optional<String> sourceMaps,
                                                                    final J2clPath sourceRoot,
                                                                    final J2clPath initialScriptFilename,
                                                                    final J2clLinePrinter logger) throws IOException {
        final Map<String, Collection<String>> arguments;

        logger.printLine("Parameter(s)");
        logger.indent();
        {
            final Path initialScriptFilenamePath = initialScriptFilename.path();
            final Path parentOf = initialScriptFilenamePath.getParent();
            Files.createDirectories(parentOf);

            arguments = Maps.sorted();

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
            arguments.put("--formatting", formatting);

            arguments.put("--js", Sets.of(sourceRoot.path().toAbsolutePath() + "/**/*.js"));

            arguments.put("--js_output_file", Sets.of(initialScriptFilenamePath.toString()));

            arguments.put("--language_out", Sets.of(languageOut.name()));

            if (sourceMaps.isPresent()) {
                arguments.put("--create_source_map", Sets.of(initialScriptFilenamePath + ".map"));
                arguments.put("--source_map_location_mapping", Sets.of(initialScriptFilename.append(sourceMaps.get()) + "|."));
                arguments.put("--output_wrapper", Sets.of("(function(){%output%}).call(this);\n//# sourceMappingURL=" + initialScriptFilename.filename() + ".map"));
                arguments.put("--assume_function_wrapper", Sets.of("true"));
            }

            logCommandLineArguments(arguments, logger);
        }
        logger.outdent();
        return arguments;
    }

    private static void logCommandLineArguments(final Map<String, Collection<String>> arguments,
                                                final J2clLinePrinter logger) {
        logger.printLine("Command line argument(s)");
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
                                         final String[] args,
                                         final boolean exportTestFunctions,
                                         final PrintStream out,
                                         final PrintStream err) {
            super(args, out, err);
            this.compiler = compiler;
            this.exportTestFunctions = exportTestFunctions;

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

        @Override
        protected CompilerOptions createOptions() {
            final CompilerOptions options = super.createOptions();
            options.setExportTestFunctions(this.exportTestFunctions);
            return options;
        }

        private final boolean exportTestFunctions;
    }

    private static CharSequence removeEmptyLines(final String text) {
        return text.replace(CRCR, LineEnding.CR.toString())
                .replace(NLCR, LineEnding.NL.toString())
                .replace(NLNL, LineEnding.NL.toString());
    }

    private final static String CRCR = "" + LineEnding.CR + LineEnding.CR;
    private final static String NLCR = "" + LineEnding.NL + LineEnding.CR;
    private final static String NLNL = "" + LineEnding.NL + LineEnding.NL;
}
