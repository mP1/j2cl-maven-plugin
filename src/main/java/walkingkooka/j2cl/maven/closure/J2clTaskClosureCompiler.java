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
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;
import java.util.Set;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
public final class J2clTaskClosureCompiler<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskClosureCompiler<>();
    }

    private J2clTaskClosureCompiler() {
        super();
    }

    @Override
    public J2clTaskResult execute(final J2clDependency artifact,
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
    public J2clTaskResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clTaskDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        final Set<J2clPath> sources = Sets.ordered();

        this.addSources(
                artifact,
                sources,
                context
        );
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
                    this.addSources(
                            dependency,
                            sources,
                            context
                    );
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
                J2clTaskResult.SUCCESS :
                J2clTaskResult.FAILED;
    }

    private void addSources(final J2clDependency artifact,
                            final Set<J2clPath> sources,
                            final C context) {
        final J2clPath transpiled = artifact.taskDirectory(J2clTaskKind.TRANSPILE_JAVA_TO_JAVASCRIPT).output();
        if (transpiled.exists().isPresent()) {
            sources.add(transpiled);
        }

        // add unpack anyway as it might contain js originally accompanying java source.
        final List<J2clPath> javaSources = context.sources(artifact);
        if (javaSources.isEmpty()) {
            artifact.artifactFile()
                    .map(sources::add);
        } else {
            sources.addAll(javaSources);
        }
    }
}
