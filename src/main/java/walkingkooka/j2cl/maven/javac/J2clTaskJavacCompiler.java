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
import walkingkooka.j2cl.maven.J2clArtifact;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;
import java.util.Set;

/**
 * Compiles the java source from sources and the given target.
 */
abstract class J2clTaskJavacCompiler<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Package private to limit sub classing.
     */
    J2clTaskJavacCompiler() {
        super();
    }

    @Override
    public J2clTaskResult execute(final J2clArtifact artifact,
                                  final J2clTaskKind kind,
                                  final C context,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                kind,
                context,
                logger
        );
    }

    @Override
    public final J2clTaskResult executeWithDirectory(final J2clArtifact artifact,
                                                     final J2clTaskDirectory directory,
                                                     final C context,
                                                     final TreeLogger logger) throws Exception {
        J2clTaskResult result = null;
        final J2clTaskKind sourceTask = this.sourceTask();

        J2clPath source = artifact.taskDirectory(sourceTask).output().exists().orElse(null);
        if (null != source) {
            final Set<J2clPath> javaSourceFiles = Sets.ordered();

            final J2clPath output = artifact.taskDirectory(sourceTask).output();

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
                    result = J2clTaskResult.SKIPPED; // project could have no source files.
                } else {
                    result = JavacCompiler.execute(bootstrap,
                            classpath,
                            javaSourceFiles,
                            directory.output().absentOrFail(),
                            context.javaCompilerArguments(),
                            shouldRunAnnotationProcessors,
                            logger) ?
                            J2clTaskResult.SUCCESS :
                            J2clTaskResult.FAILED;
                    if (J2clTaskResult.SUCCESS == result) {
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
            result = J2clTaskResult.ABORTED;
        }

        return result;
    }

    /**
     * A previous task that has source in its output ready for compiling
     */
    abstract J2clTaskKind sourceTask();

    /**
     * This task this is used to build the classpath for the java compiler.
     */
    abstract List<J2clTaskKind> compileTask();

    /**
     * Returns whether annotations processors should be run.
     */
    abstract boolean shouldRunAnnotationProcessors();

    /**
     * Adds entries to either the bootstrap or classpath
     */
    final void buildBootstrapAndClasspath(final J2clArtifact artifact,
                                          final boolean shouldRunAnnotationProcessors,
                                          final Set<J2clPath> bootstrap,
                                          final Set<J2clPath> classpath) {
        for (final J2clArtifact dependency : artifact.dependencies()) {
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
     * Sub classes implement two different ways of finding the right class files for the given {@link J2clArtifact}.
     */
    abstract J2clPath selectClassFiles(final J2clArtifact artifact);

    /**
     * This is called after the compile
     */
    abstract void postCompile(final J2clArtifact artifact,
                              final J2clTaskDirectory directory,
                              final C context,
                              final TreeLogger logger) throws Exception;
}
