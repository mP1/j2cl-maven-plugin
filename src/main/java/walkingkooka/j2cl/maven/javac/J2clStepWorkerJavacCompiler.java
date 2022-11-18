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

package walkingkooka.j2cl.maven.javac;

import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepDirectory;
import walkingkooka.j2cl.maven.J2clStepResult;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;
import java.util.Set;

/**
 * Compiles the java source from sources and the given target.
 */
abstract class J2clStepWorkerJavacCompiler<C extends J2clMavenContext> implements J2clStepWorker<C> {

    /**
     * Package private to limit sub classing.
     */
    J2clStepWorkerJavacCompiler() {
        super();
    }

    @Override
    public J2clStepResult execute(final J2clDependency artifact,
                                  final J2clStep step,
                                  final C context,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                step,
                context,
                logger
        );
    }

    @Override
    public final J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                                     final J2clStepDirectory directory,
                                                     final C context,
                                                     final TreeLogger logger) throws Exception {
        J2clStepResult result = null;
        final J2clStep sourceStep = this.sourcesStep();

        J2clPath source = artifact.step(sourceStep).output().exists().orElse(null);
        if (null != source) {
            final Set<J2clPath> javaSourceFiles = Sets.ordered();

            final J2clPath output = artifact.step(sourceStep).output();

            javaSourceFiles.addAll(output.gatherFiles((path) -> false == output.isSuperSource(path) && J2clPath.JAVA_FILES.test(path)));
            if (javaSourceFiles.isEmpty()) {
                source = null;
            } else {
                final boolean shouldRunAnnotationProcessors = this.shouldRunAnnotationProcessors();

                final Set<J2clPath> bootstrap = Sets.ordered();
                final Set<J2clPath> classpath = Sets.ordered();

                // add source to classpath, might be useful as it may contain non java files that are needed by annotation processors.
                if(shouldRunAnnotationProcessors) {
                    classpath.add(output);
                }

                this.buildBootstrapAndClasspath(artifact, shouldRunAnnotationProcessors, bootstrap, classpath);

                if (classpath.isEmpty()) {
                    result = J2clStepResult.SKIPPED; // project could have no source files.
                } else {
                    result = JavacCompiler.execute(bootstrap,
                            classpath,
                            javaSourceFiles,
                            directory.output().absentOrFail(),
                            context.javaCompilerArguments(),
                            shouldRunAnnotationProcessors,
                            logger) ?
                            J2clStepResult.SUCCESS :
                            J2clStepResult.FAILED;
                    if (J2clStepResult.SUCCESS == result) {
                        this.postCompile(
                                artifact,
                                directory,
                                context,
                                logger
                        );
                    }
                }
            }
        }

        if (null == source) {
            logger.indentedLine("No files found");
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
    abstract List<J2clStep> compiledStep();

    /**
     * Returns whether annotations processors should be run.
     */
    abstract boolean shouldRunAnnotationProcessors();

    /**
     * Adds entries to either the bootstrap or classpath
     */
    final void buildBootstrapAndClasspath(final J2clDependency artifact,
                                          final boolean shouldRunAnnotationProcessors,
                                          final Set<J2clPath> bootstrap,
                                          final Set<J2clPath> classpath) {
        //final J2clStep compiledStep = this.compiledStep();

        for (final J2clDependency dependency : artifact.dependencies()) {
            if (dependency.isAnnotationClassFiles()) {
                classpath.add(dependency.artifactFileOrFail());
                continue;
            }

            // not running ap dont add to cp
            if (dependency.isAnnotationProcessor() && false == shouldRunAnnotationProcessors) {
                continue;
            }

            if (dependency.isJreBootstrapClassFiles()) {
                bootstrap.add(dependency.artifactFileOrFail());
                continue;
            }

            if (dependency.isClasspathRequired()) {
                classpath.add(this.selectClassFiles(dependency));
                continue;
            }

            if (dependency.isIgnored()) {
                continue;
            }

            if (dependency.isJavascriptSourceRequired()) {
                continue;
            }

            if (dependency.isJreClassFiles()) {
                classpath.add(dependency.artifactFileOrFail());
                continue;
            }

            classpath.add(this.selectClassFiles(dependency));
        }
    }

    /**
     * Sub classes implement two different ways of finding the right class files for the given {@link J2clDependency}.
     */
    abstract J2clPath selectClassFiles(final J2clDependency dependency);

    /**
     * This is called after the compile
     */
    abstract void postCompile(final J2clDependency artifact,
                              final J2clStepDirectory directory,
                              final C context,
                              final TreeLogger logger) throws Exception;
}
