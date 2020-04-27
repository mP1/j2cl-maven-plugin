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
abstract class J2clStepWorkerJavacCompiler extends J2clStepWorker2 {

    /**
     * Package private to limit sub classing.
     */
    J2clStepWorkerJavacCompiler() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        J2clStepResult result = null;
        final J2clStep sourceStep = this.sourcesStep();

        J2clPath source = artifact.step(sourceStep).output().exists().orElse(null);
        if (null != source) {
            final List<J2clPath> javaSourceFiles = Lists.array();

            final J2clPath output = artifact.step(sourceStep).output();
            
            javaSourceFiles.addAll(output.gatherFiles((path) -> false == output.isSuperSource(path) && J2clPath.JAVA_FILES.test(path)));
            if (javaSourceFiles.isEmpty()) {
                source = null;
            } else {
                final List<J2clPath> bootstrap = Lists.array();
                final List<J2clPath> classpath = Lists.array();

                final J2clStep compiledStep = this.compiledStep();

                for (final J2clDependency dependency : artifact.dependencies()) {
                    if (dependency.isJreBootstrapClassFiles()) {
                        bootstrap.add(dependency.artifactFileOrFail());
                        continue;
                    }

                    if (dependency.isClasspathRequired()) {
                        classpath.add(dependency.artifactFileOrFail());
                        continue;
                    }

                    dependency.step(compiledStep)
                            .output()
                            .exists()
                            .ifPresent(classpath::add);
                }

                if (classpath.isEmpty()) {
                    result = J2clStepResult.SKIPPED; // project could have no source files.
                } else {
                    result = JavacCompiler.execute(bootstrap,
                            classpath,
                            javaSourceFiles,
                            directory.output().absentOrFail(),
                            artifact.request().javaCompilerArguments(),
                            this.shouldRunAnnotationProcessors(),
                            logger) ?
                            J2clStepResult.SUCCESS :
                            J2clStepResult.FAILED;
                    if (J2clStepResult.SUCCESS == result) {
                        this.postCompile(artifact, directory, logger);
                    }
                }
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

    /**
     * This step this is used to build the classpath for the java compiler.
     */
    abstract J2clStep compiledStep();

    /**
     * Returns whether annotations processors should be run.
     */
    abstract boolean shouldRunAnnotationProcessors();

    /**
     * This is called after the compile
     */
    abstract void postCompile(final J2clDependency artifact,
                              final J2clStepDirectory directory,
                              final J2clLinePrinter logger) throws Exception;
}
