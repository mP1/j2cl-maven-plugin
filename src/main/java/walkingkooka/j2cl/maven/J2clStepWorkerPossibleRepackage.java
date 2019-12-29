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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scans the output of the previous step for any repackage super source and if any are found writes the result to an output directory.
 */
final class J2clStepWorkerPossibleRepackage extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerPossibleRepackage();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerPossibleRepackage() {
        super();
    }

    @Override
    J2clStepResult execute1(final J2clDependency artifact,
                            final J2clStepDirectory directory,
                            final J2clLinePrinter logger) throws Exception {
        final J2clStepResult result;

        if (artifact.isProcessingSkipped()) {
            result = J2clStepResult.SKIPPED;
        } else {

            // the package prefix file will be present in UNPACK
            final J2clPath packagePrefix = artifact.step(J2clStep.UNPACK)
                    .output().append(J2clPath.PACKAGE_PREFIX_FILE);
            logger.printLine(packagePrefix.toString());
            logger.indent();
            {
                if (packagePrefix.isFile()) {
                    this.copyAndRepackage(artifact.step(J2clStep.GWT_INCOMPATIBLE_STRIP).output(),
                            packagePrefix.readPackagePrefix(),
                            directory.output(),
                            logger);
                    result = J2clStepResult.SUCCESS;
                } else {
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
    private void copyAndRepackage(final J2clPath sourceRoot,
                                  final String packagePrefix,
                                  final J2clPath output,
                                  final J2clLinePrinter logger) throws Exception {
        final Set<J2clPath> files = sourceRoot.gatherFiles(J2clPath.JAVA_FILES);
        final J2clPath refactorSourceRoot;
        final Set<J2clPath> refactorFiles;

        logger.indent();
        {
            final String find = packagePrefix;
            final int lastPackage = packagePrefix.lastIndexOf('.');
            final String replace = packagePrefix.substring(lastPackage + 1);

            logger.printLine("Finding package prefix " + CharSequences.quote(find) + " replacing with " + CharSequences.quote(replace) + " in java source");
            logger.indent();
            {
                refactorSourceRoot = sourceRoot.append(find.substring(0, lastPackage)
                .replace('.', File.separatorChar));

                // filter only files belonging to refactor source root
                refactorFiles = files.stream()
                        .filter(f -> f.path().startsWith(refactorSourceRoot.path()))
                        .collect(Collectors.toCollection(Sets::sorted));

                // copy and refactor java source and copy other files to output.
                output.copyFiles(refactorSourceRoot,
                        refactorFiles,
                        (content, path) -> {
                            return path.isJava() ?
                                    refactor(content, find, replace) :
                                    content;
                        },
                        logger);
            }
            logger.outdent();

            logger.printLine("Copying other files");
            logger.indent();
            {

                // remove the refactored files and copy those...
                final Set<J2clPath> otherFiles = Sets.sorted();
                otherFiles.addAll(files);
                otherFiles.removeAll(refactorFiles);

                // copy all other files verbatim.
                output.copyFiles(sourceRoot,
                        otherFiles,
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
