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

        if (artifact.isIgnored()) {
            sourceRoot = artifact.step(J2clStep.UNPACK).output();
        } else {
            sourceRoot = shadeOrCompileGwtIncompatibleStripped(artifact, J2clStep.SHADE_JAVA_SOURCE, J2clStep.GWT_INCOMPATIBLE_STRIP);
        }

        logger.printLine("Preparing...");
        logger.printIndented("Source path(s)", sourceRoot);

        final List<J2clPath> classpath = artifact.dependencies()
                .stream()
                .filter(d -> false == d.isAnnotationProcessor())
                .map(d -> d.isIgnored() ?
                        d.artifactFileOrFail() :
                        shadeOrCompileGwtIncompatibleStripped(d, J2clStep.SHADE_CLASS_FILES, J2clStep.COMPILE_GWT_INCOMPATIBLE_STRIPPED))
                .flatMap(d -> d.exists().stream())
                .collect(Collectors.toList());

        return J2clTranspiler.execute(classpath,
                sourceRoot,
                directory.output().absentOrFail(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }

    /**
     * Tries the $first/output and if that is absent tries $second/output.
     */
    private static J2clPath shadeOrCompileGwtIncompatibleStripped(final J2clDependency dependency,
                                                                  final J2clStep tryFirst,
                                                                  final J2clStep second) {
        return dependency.step(tryFirst)
                .output()
                .exists()
                .orElseGet(() -> dependency.step(second).output());
    }
}
