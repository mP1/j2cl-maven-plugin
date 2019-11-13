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
 * Compiles the java source from sources and the given target.
 */
abstract class J2ClStepWorkerJavacCompiler extends J2ClStepWorker2 {

    /**
     * Package private to limit sub classing.
     */
    J2ClStepWorkerJavacCompiler() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        J2clStepResult result = null;
        final J2clStep step = this.sourcesStep();

        J2clPath source = artifact.step(step).output().exists().orElse(null);
        if (null != source) {
            final List<J2clPath> javaSourceFiles = Lists.array();
            javaSourceFiles.addAll(artifact.step(step)
                    .output()
                    .gatherFiles(J2clPath.JAVA_FILES));
            if (javaSourceFiles.isEmpty()) {
                source = null;
            } else {
                final List<J2clPath> classpath = Lists.array();

                for (final J2clDependency dependency : artifact.classpathAndDependencies()) {
                    if(dependency.dependencies().contains(artifact)) {
                        continue; // dont add a classpath required that is a parent of this artifact.
                    }

                    if (dependency.isProcessingSkipped()) {
                        classpath.add(dependency.artifactFileOrFail());
                    } else {
                        classpath.add(dependency.step(J2clStep.COMPILE_GWT_INCOMPATIBLE_STRIPPED).output());
                    }
                }

                result = JavacCompiler.execute(classpath.subList(0, 1),
                        classpath.subList(1, classpath.size()),
                        javaSourceFiles,
                        directory.output().emptyOrFail(),
                        logger) ?
                        J2clStepResult.SUCCESS :
                        J2clStepResult.FAILED;
            }
        }

        if (null == source) {
            logger.printIndentedLine("No files found");
            result = J2clStepResult.ABORTED;
        }

        return result;
    }

    /**
     * A previous step that has source in its output ready for compiling
     */
    abstract J2clStep sourcesStep();
}
