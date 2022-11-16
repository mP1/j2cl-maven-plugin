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

import walkingkooka.j2cl.maven.closure.J2clStepWorkerClosureCompiler;
import walkingkooka.j2cl.maven.hash.J2clStepWorkerHash;
import walkingkooka.j2cl.maven.javac.J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource;
import walkingkooka.j2cl.maven.javac.J2clStepWorkerJavacCompilerUnpackedSource;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.j2cl.maven.output.J2clStepWorkerOutputAssembler;
import walkingkooka.j2cl.maven.shade.J2clStepWorkerShadeClassFile;
import walkingkooka.j2cl.maven.shade.J2clStepWorkerShadeJavaSource;
import walkingkooka.j2cl.maven.strip.J2clStepWorkerGwtIncompatibleStripPreprocessor;
import walkingkooka.j2cl.maven.test.J2clStepWorkerWebDriverUnitTestRunner;
import walkingkooka.j2cl.maven.transpile.J2clStepWorkerJ2clTranspiler;
import walkingkooka.j2cl.maven.unpack.J2clStepWorkerUnpack;

@SuppressWarnings("StaticInitializerReferencesSubClass")
public interface J2clStepWorker {

    J2clStepWorker HASH = J2clStepWorkerHash.instance();

    J2clStepWorker UNPACK = J2clStepWorkerUnpack.instance();

    J2clStepWorker COMPILE_SOURCE = J2clStepWorkerJavacCompilerUnpackedSource.instance();

    J2clStepWorker STRIP_GWT_INCOMPAT = J2clStepWorkerGwtIncompatibleStripPreprocessor.instance();

    J2clStepWorker COMPILE_STRIP_GWT_INCOMPAT = J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource.instance();

    J2clStepWorker SHADE_JAVA_SOURCE = J2clStepWorkerShadeJavaSource.instance();

    J2clStepWorker SHADE_CLASS_FILE = J2clStepWorkerShadeClassFile.instance();

    J2clStepWorker TRANSPILER = J2clStepWorkerJ2clTranspiler.instance();

    J2clStepWorker CLOSURE = J2clStepWorkerClosureCompiler.instance();

    J2clStepWorker OUTPUT_ASSEMBLER = J2clStepWorkerOutputAssembler.instance();

    J2clStepWorker JUNIT_WEBDRIVER_TESTS = J2clStepWorkerWebDriverUnitTestRunner.instance();

    J2clStepResult execute(final J2clDependency artifact,
                           final J2clStep step,
                           final TreeLogger logger) throws Exception;

    default J2clStepResult executeIfNecessary(final J2clDependency artifact,
                                              final J2clStep step,
                                              final TreeLogger logger) throws Exception {
        final J2clStepResult result;

        final J2clStepDirectory directory = artifact.step(step);

        logger.line("Directory");
        logger.indent();
        {
            logger.line(directory.toString());
            logger.indent();
            {
                if (directory.successful().exists().isPresent()) {
                    logger.indentedLine("Cache success result present and will be kept");

                    result = J2clStepResult.SUCCESS;
                } else {
                    if (directory.aborted().exists().isPresent()) {
                        logger.indentedLine("Cache abort result present and will be kept");

                        result = J2clStepResult.ABORTED;
                    } else {
                        if (directory.skipped().exists().isPresent()) {
                            logger.indentedLine("Cache skip result present and will be kept");

                            result = J2clStepResult.SKIPPED;
                        } else {
                            final J2clPath path = directory.path();
                            if (path.exists().isPresent()) {
                                path.removeAll();

                                logger.indentedLine("Removed all files");
                            }
                            path.createIfNecessary();

                            // aborted steps for the project are transformed into skipped.
                            final J2clStepResult result1 = this.executeWithDirectory(
                                    artifact,
                                    directory,
                                    logger
                            );
                            result = J2clStepResult.ABORTED == result1 && false == artifact.isDependency() ?
                                    J2clStepResult.SKIPPED :
                                    result1;
                        }
                    }
                }
            }
            logger.outdent();
        }
        logger.outdent();

        return result;
    }

    J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                        final J2clStepDirectory directory,
                                        final TreeLogger logger) throws Exception;
}
