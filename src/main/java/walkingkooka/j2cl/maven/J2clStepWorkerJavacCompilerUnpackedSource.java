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
import walkingkooka.text.CharSequences;

import java.nio.file.Files;
import java.util.List;

/**
 * Compiles the java source to the target {@link J2clStepDirectory#output()}, with annotation processors enabled.
 */
final class J2clStepWorkerJavacCompilerUnpackedSource extends J2clStepWorkerJavacCompiler {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJavacCompilerUnpackedSource();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerJavacCompilerUnpackedSource() {
        super();
    }

    @Override
    J2clStep sourcesStep() {
        return J2clStep.UNPACK;
    }

    @Override
    List<J2clStep> compiledStep() {
        return Lists.of(J2clStep.JAVAC_COMPILE);
    }

    @Override
    boolean shouldRunAnnotationProcessors() {
        return true;
    }

    /**
     * Always add the dependency jar file.
     */
    @Override
    J2clPath selectClassFiles(final J2clDependency dependency) {
        return dependency.artifactFileOrFail();
    }

    /**
     * If executing a test fixup the name of the generated javascript file.
     */
    @Override
    void postCompile(final J2clDependency artifact,
                     final J2clStepDirectory directory,
                     final J2clLinePrinter logger) throws Exception {
        final J2clRequest request = artifact.request();
        if (false == artifact.isDependency() && J2clSourcesKind.TEST == request.sourcesKind()) {
            this.junitProcessorPostFix(artifact, directory, logger);
        }
    }

    private void junitProcessorPostFix(final J2clDependency artifact,
                                       final J2clStepDirectory directory,
                                       final J2clLinePrinter logger) throws Exception {
        logger.indent();
        {
            logger.printLine("Junit processor post fixup");
            logger.indent();
            {
                final J2clRequest request = artifact.request();
                final J2clPath output = directory.output();

                for (final String testSuiteClassName : request.entryPoints()) {
                    logger.printLine(testSuiteClassName );

                    final String testClassName = extractTestClassName(testSuiteClassName);
                    final J2clPath generatedFilename = output.testAdapterSuiteGeneratedFilename(testClassName);
                    final J2clPath correctFilename = output.testAdapterSuiteCorrectFilename(testClassName);

                    Files.copy(generatedFilename.path(), correctFilename.path());
                }
            }
            logger.outdent();
        }
        logger.outdent();
    }

    // 'javatests.org.gwtproject.timer.client.TimerJ2clTest_AdapterSuite' -> org.gwtproject.timer.client.TimerJ2clTest
    static String extractTestClassName(final String typeName) {
        return CharSequences.subSequence(typeName, "javatests.".length(), -"_AdapterSuite".length())
                .toString();
    }
}
