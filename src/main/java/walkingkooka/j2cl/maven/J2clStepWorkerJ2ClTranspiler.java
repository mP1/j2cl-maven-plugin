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

import java.util.List;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
final class J2clStepWorkerJ2ClTranspiler extends J2ClStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJ2ClTranspiler();
    }

    private J2clStepWorkerJ2ClTranspiler() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        // in the end only the project is compiled, all other dependencies remain untouched.
        final J2clPath sourceRoot = artifact.step(artifact.isProcessingSkipped() ?
                J2clStep.UNPACK :
                J2clStep.GWT_INCOMPATIBLE_STRIP)
                .output();

        logger.print("Preparing...");
        logger.printIndented("Source path(s)", sourceRoot);

        final List<J2clPath> classpath = Lists.array();

        for (final J2clDependency dependency : artifact.classpathAndDependencies()) {
            if (dependency.isProcessingSkipped()) {
                classpath.add(dependency.artifactFileOrFail());
            } else {
                classpath.add(dependency.step(J2clStep.COMPILE_GWT_INCOMPATIBLE_STRIPPED).output());
            }
        }

        return J2clTranspiler.execute(classpath,
                sourceRoot,
                directory.output().emptyOrFail(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }
}
