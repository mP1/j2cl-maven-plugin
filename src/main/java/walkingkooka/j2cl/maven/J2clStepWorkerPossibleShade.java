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

import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Scans the output of the previous step for any shade super source and if any are found writes the result to an output directory.
 */
final class J2clStepWorkerPossibleShade extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerPossibleShade();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerPossibleShade() {
        super();
    }

    @Override
    J2clStepResult execute1(final J2clDependency artifact,
                            final J2clStepDirectory directory,
                            final J2clLinePrinter logger) throws Exception {
        J2clStepResult result = null;

        if (artifact.isProcessingSkipped()) {
            result = J2clStepResult.SKIPPED;
        } else {

            final J2clPath shade = artifact.step(J2clStep.UNPACK)
                    .output()
                    .shadeFile();
            logger.printLine(shade.toString());
            logger.indent();
            {
                boolean repackaging = false;

                if (shade.isFile()) {
                    final Map<String, String> shadeMappings = shade.readShadeFile();

                    if (!shadeMappings.isEmpty()) {
                        this.copyAndShade(artifact.step(J2clStep.GWT_INCOMPATIBLE_STRIP).output(),
                                shadeMappings,
                                directory.output(),
                                logger);
                        repackaging = true;
                        result = J2clStepResult.SUCCESS;
                    }
                }

                if (!repackaging) {
                    logger.printLine("Not found");
                    result = J2clStepResult.SKIPPED;
                }
            }
            logger.outdent();
        }

        return result;
    }

    /**
     * Performs two copy passes, the first will refactor any java source during the copy process, the second will simply
     * copy the files to the destination.
     */
    private void copyAndShade(final J2clPath sourceRoot,
                                  final Map<String, String> repackaging,
                                  final J2clPath output,
                                  final J2clLinePrinter logger) throws Exception {
        final Set<J2clPath> files = sourceRoot.gatherFiles(J2clPath.JAVA_FILES);
        final Set<J2clPath> nonRefactoredFiles = Sets.sorted();
        nonRefactoredFiles.addAll(files);

        logger.indent();
        {
            for (final Entry<String, String> mapping : repackaging.entrySet()) {
                final String find = mapping.getKey();
                final String replace = mapping.getValue();

                final J2clPath shadeDirectory = replace.isEmpty() ?
                        output :
                        output.append(replace.replace('.', File.separatorChar));

                logger.printLine("Finding package " + CharSequences.quote(find) + " replacing with " + CharSequences.quote(replace) + " in java source");
                logger.indent();
                {
                    final Set<J2clPath> refactorFiles = Sets.sorted();
                    final J2clPath refactorSourceRoot = sourceRoot.append(find.replace('.', File.separatorChar));

                    // filter only files belonging to refactor source root
                    files.stream()
                            .filter(f -> f.path().startsWith(refactorSourceRoot.path()))
                            .forEach(refactorFiles::add);

                    nonRefactoredFiles.removeAll(refactorFiles);

                    // copy and refactor java source and copy other files to output.
                    shadeDirectory.copyFiles(refactorSourceRoot,
                            refactorFiles,
                            (content, path) -> {
                                return path.isJava() ?
                                        refactor(content, find, replace) :
                                        content;
                            },
                            logger);
                }
                logger.outdent();
            }

            logger.printLine("Copying other files");
            logger.indent();
            {

                // copy all other files verbatim.
                output.copyFiles(sourceRoot,
                        nonRefactoredFiles,
                        logger);

            }
            logger.outdent();
        }
        logger.outdent();
    }

    /**
     * Reads the text file assuming its java source and removes the package prefix. Should have the intended effect
     * of fixing package declarations, import statements and other fully qualified class names.
     */
    private static byte[] refactor(final byte[] content,
                                   final String find,
                                   final String replace) {
        final Charset charset = Charset.defaultCharset();
        String text = new String(content, charset);

        // TODO nasty simply remove the package prefix, replace with javaparser that transforms java source imports, fqcns etc.
        text = text.replace(find, replace);

        return text.getBytes(charset);
    }
}
