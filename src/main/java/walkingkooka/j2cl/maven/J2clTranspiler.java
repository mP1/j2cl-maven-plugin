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

import com.google.j2cl.common.Problems;
import com.google.j2cl.transpiler.J2clTranspilerOptions;
import walkingkooka.collect.list.Lists;
import walkingkooka.text.CharSequences;

import java.util.List;
import java.util.stream.Collectors;

final class J2clTranspiler {

    static boolean execute(final List<J2clPath> classpath,
                           final List<J2clPath> sourceFiles,
                           final J2clPath output,
                           final J2clLinePrinter logger) {
        logger.printLine("J2clTranspiler");
        logger.indent();

        boolean success;
        {
            final List<J2clPath> javaInput = Lists.array();
            final List<J2clPath> nativeInput = Lists.array();
            final List<J2clPath> other = Lists.array();

            sourceFiles.forEach(f -> {
                final String filename = f.filename();
                if (CharSequences.endsWith(filename, ".java")) {
                    javaInput.add(f);
                } else {
                    if (CharSequences.endsWith(filename, ".native.js")) {
                        nativeInput.add(f);
                    } else {
                        other.add(f);
                    }
                }
            });

            logger.printLine("Parameters");
            logger.indent();
            {
                logger.printIndented("Classpath(s)", classpath);
                logger.printIndented("Source(s)", javaInput);
                logger.printIndented("Native source(s)", nativeInput);
                logger.printIndented("Other (ignored) file(s)", other);
                logger.printIndented("Output", output);
            }
            logger.outdent();

            logger.printLine("J2clTranspiler");
            logger.indent();
            {
                final J2clTranspilerOptions options = J2clTranspilerOptions.newBuilder()
                        .setClasspaths(classpath.stream()
                                .map(J2clPath::toString)
                                .collect(Collectors.toList())
                        )
                        .setOutput(output.path())
                        .setDeclareLegacyNamespace(false)//TODO parameterize these? copied straight from vertispan/j2clmavenplugin
                        .setEmitReadableLibraryInfo(false)
                        .setEmitReadableSourceMap(false)
                        .setGenerateKytheIndexingMetadata(false)
                        .setSources(J2clPath.toFileInfo(javaInput))
                        .setNativeSources(J2clPath.toFileInfo((nativeInput)))
                        .build();
                final Problems problems = com.google.j2cl.transpiler.J2clTranspiler.transpile(options);
                success = !problems.hasErrors();

                final List<String> messages = problems.getMessages();
                final int count = messages.size();

                logger.printLine(count + " problem(s)");
                {
                    logger.indent();
                    {
                        logger.indent();
                        messages.forEach(logger::printLine);

                        logger.outdent();
                    }
                    logger.printEndOfList();
                    logger.outdent();
                }
            }
        }
        logger.outdent();

        return success;
    }
}
