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

package walkingkooka.j2cl.maven.transpile;

import com.google.j2cl.common.OutputUtils;
import com.google.j2cl.common.Problems;
import com.google.j2cl.common.SourceUtils.FileInfo;
import com.google.j2cl.transpiler.J2clTranspilerOptions;
import com.google.j2cl.transpiler.frontend.Frontend;
import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class J2clTranspiler {

    static boolean execute(final Collection<J2clPath> classpath,
                           final List<J2clPath> sourcePaths,
                           final J2clPath output,
                           final TreeLogger logger) throws IOException {
        logger.line("J2clTranspiler");
        logger.indent();

        boolean success;
        {
            final List<FileInfo> javaInput = Lists.array();
            final List<FileInfo> nativeJsInput = Lists.array();
            final List<J2clPath> jsInput = Lists.array();// probably js
            final List<J2clPath> nativeJsAndJsInput = Lists.array();

            for (final J2clPath sourcePath : sourcePaths) {
                if (sourcePath.exists().isPresent()) {
                    sourcePath.gatherFiles(
                                    J2clPath.JAVA_FILES.or(J2clPath.JAVASCRIPT_FILES.or(J2clPath.NATIVE_JAVASCRIPT_FILES))
                            )
                            .forEach(f -> {
                                final String filename = f.filename();
                                if (CharSequences.endsWith(filename, ".java")) {
                                    javaInput.add(f.toFileInfo(sourcePath));
                                } else {
                                    if (CharSequences.endsWith(filename, ".native.js")) {
                                        nativeJsInput.add(f.toFileInfo(sourcePath));
                                    } else {
                                        if (CharSequences.endsWith(filename, ".js")) {
                                            jsInput.add(f);
                                        }
                                    }
                                    nativeJsAndJsInput.add(f);
                                }
                            });
                }
            }

            if (logger.isDebugEnabled()) {
                logger.line("Parameters");
                logger.indent();
                {
                    logger.paths("Classpath(s)", classpath, TreeFormat.FLAT);
                    logger.fileInfos("*.java source(s)", javaInput, TreeFormat.TREE);
                    logger.fileInfos("*.native.js source(s)", nativeJsInput, TreeFormat.TREE);
                    logger.paths("*.js source(s)", jsInput, TreeFormat.TREE);
                    logger.path("Output", output);
                }
                logger.outdent();
            }

            logger.line("J2clTranspiler");
            logger.indent();
            {
                final Problems problems = new Problems();

                try (final OutputUtils.Output outputOutput = OutputUtils.initOutput(
                        output.path(),
                        problems
                )
                ) {
                    final J2clTranspilerOptions options = J2clTranspilerOptions.newBuilder()
                            .setClasspaths(classpath.stream()
                                    .map(J2clPath::toString)
                                    .collect(Collectors.toList())
                            ).setOutput(outputOutput)
                            .setEmitReadableLibraryInfo(false)
                            .setEmitReadableSourceMap(false)
                            .setFrontend(Frontend.JDT)
                            .setGenerateKytheIndexingMetadata(false)
                            .setSources(javaInput)
                            .setNativeSources(nativeJsInput)
                            .build(problems);

                    com.google.j2cl.transpiler.J2clTranspiler.transpile(
                            options,
                            problems
                    );
                }

                success = false == problems.hasErrors();

                logger.strings("Error(s)", problems.getErrors());
                logger.strings("Warnings(s)", problems.getWarnings());
                logger.strings("Message(s)", problems.getMessages());

                if (success) {
                    logger.line("Copy js to output");
                    logger.indent();
                    {
                        for (final J2clPath sourcePath : sourcePaths) {
                            output.copyFiles(
                                    sourcePath,
                                    jsInput,
                                    J2clPath.COPY_FILE_CONTENT_VERBATIM
                            );
                        }
                    }
                    logger.outdent();

                    logger.paths(
                            "Output",
                            output.gatherFiles(J2clPath.ALL_FILES),
                            TreeFormat.TREE
                    );
                }
            }
        }
        logger.outdent();

        return success;
    }
}
