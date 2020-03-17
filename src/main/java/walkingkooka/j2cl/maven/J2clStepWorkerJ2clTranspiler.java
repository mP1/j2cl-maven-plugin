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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
final class J2clStepWorkerJ2clTranspiler extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJ2clTranspiler();
    }

    private J2clStepWorkerJ2clTranspiler() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        final J2clPath sourceRoot;

        if (artifact.isProcessingSkipped()) {
            sourceRoot = artifact.step(J2clStep.UNPACK).output();
        } else {
            // source may have been shaded, have to check if a shade output directory exists.
            final J2clPath possibleShade = artifact.step(J2clStep.SHADE_JAVA_SOURCE).output();
            if (possibleShade.exists().isPresent()) {
                sourceRoot = possibleShade;
            } else {
                sourceRoot = artifact.step(J2clStep.GWT_INCOMPATIBLE_STRIP).output();
            }
        }

        logger.printLine("Preparing...");
        logger.printIndented("Source path(s)", sourceRoot);

        final List<J2clPath> classpath = artifact.classpathAndDependencies()
                .stream()
                .map(d -> d.isProcessingSkipped() ?
                        d.artifactFileOrFail() :
                        d.step(J2clStep.COMPILE_GWT_INCOMPATIBLE_STRIPPED).output())
                .flatMap(d -> d.exists().stream())
                .collect(Collectors.toList());

        return J2clTranspiler.execute(classpath,
                sourceRoot,
                directory.output().emptyOrFail(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }
}
