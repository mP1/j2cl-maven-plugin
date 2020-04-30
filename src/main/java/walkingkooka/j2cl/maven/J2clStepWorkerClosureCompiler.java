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

import walkingkooka.collect.set.Sets;

import java.util.Optional;
import java.util.Set;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
final class J2clStepWorkerClosureCompiler extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerClosureCompiler();
    }

    private J2clStepWorkerClosureCompiler() {
        super();
    }

    @Override final J2clStepResult execute1(final J2clDependency artifact,
                                            final J2clStepDirectory directory,
                                            final J2clLinePrinter logger) throws Exception {
        final J2clRequest request = artifact.request();
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
                    addIfAbsent(dependency.artifactFileOrFail(), sources);
                    continue;
                }

                if (dependency.isJreJavascriptFiles()) {
                    addIfAbsent(dependency.artifactFileOrFail(), sources);
                    continue;
                }

                if (dependency.isJavascriptSourceRequired()) {
                    this.addSources(dependency, sources);
                    continue;
                }
            }
        }

        return ClosureCompiler.compile(request.level(),
                request.defines(),
                request.entryPoints(),
                request.externs(),
                request.formatting(),
                request.languageOut(),
                request.sourcesKind() == J2clSourcesKind.TEST,
                sources,
                directory.output().createIfNecessary(),
                request.initialScriptFilename().filename(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }

    private void addSources(final J2clDependency artifact,
                            final Set<J2clPath> sources) {
        final J2clPath transpiled = artifact.step(J2clStep.TRANSPILE).output();
        if (transpiled.exists().isPresent()) {
            addIfAbsent(transpiled, sources);
        }

        // add unpack anyway as it might contain js originally accompanying java source.
        final J2clPath unpack = artifact.step(J2clStep.UNPACK).output();
        if (unpack.exists().isPresent()) {
            addIfAbsent(unpack, sources);
        } else {
            final Optional<J2clPath> file = artifact.artifactFile();
            if (file.isPresent()) {
                addIfAbsent(file.get(), sources);
            }
        }
    }
}
