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

package walkingkooka.j2cl.maven.closure;

import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clSourcesKind;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepDirectory;
import walkingkooka.j2cl.maven.J2clStepResult;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.Set;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
public final class J2clStepWorkerClosureCompiler<C extends J2clMavenContext> implements J2clStepWorker<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clStepWorker<C> instance() {
        return new J2clStepWorkerClosureCompiler<>();
    }

    private J2clStepWorkerClosureCompiler() {
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
    public J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clStepDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        final Set<J2clPath> sources = Sets.ordered();

        this.addSources(artifact, sources);
        {
            for (final J2clDependency dependency : artifact.dependencies()) {
                if (dependency.isAnnotationClassFiles()) {
                    continue;
                }

                if (dependency.isAnnotationProcessor()) {
                    continue;
                }

                if (dependency.isIgnored()) {
                    continue;
                }

                if (dependency.isJreBootstrapClassFiles()) {
                    continue;
                }

                if (dependency.isJreClassFiles()) {
                    continue;
                }

                if (dependency.isJreJavascriptBootstrapFiles()) {
                    sources.add(dependency.artifactFileOrFail());
                    continue;
                }

                if (dependency.isJreJavascriptFiles()) {
                    sources.add(dependency.artifactFileOrFail());
                    continue;
                }

                if (dependency.isJavascriptSourceRequired()) {
                    this.addSources(dependency, sources);
                    continue;
                }
            }
        }

        return ClosureCompiler.compile(
                context.level(),
                context.defines(),
                context.entryPoints(),
                context.externs(),
                context.formatting(),
                context.languageOut(),
                context.sourcesKind() == J2clSourcesKind.TEST,
                context.sourceMaps(),
                sources,
                directory.output().createIfNecessary(),
                context.initialScriptFilename().filename(),
                context,
                logger
        ) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }

    private void addSources(final J2clDependency artifact,
                            final Set<J2clPath> sources) {
        final J2clPath transpiled = artifact.step(J2clStep.TRANSPILE_JAVA_TO_JAVASCRIPT).output();
        if (transpiled.exists().isPresent()) {
            sources.add(transpiled);
        }

        // add unpack anyway as it might contain js originally accompanying java source.
        final J2clPath unpack = artifact.step(J2clStep.UNPACK).output();
        if (unpack.exists().isPresent()) {
            sources.add(unpack);
        } else {
            artifact.artifactFile().map(sources::add);
        }
    }
}