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
 * Calls the closure compiler and assembles the final Javascript output.
 */
final class J2ClBuildStepWorkerClosureCompiler extends J2ClBuildStepWorker2 {

    /**
     * Singleton
     */
    static J2clBuildStepWorker instance() {
        return new J2ClBuildStepWorkerClosureCompiler();
    }

    private J2ClBuildStepWorkerClosureCompiler() {
        super();
    }

    @Override
    final J2clBuildStepResult execute1(final J2clDependency artifact,
                                       final J2clStepDirectory directory,
                                       final J2clLinePrinter logger) throws Exception {
        final J2clBuildRequest request = artifact.request();
        final List<J2clPath> sources;

        logger.printLine("Discovering source roots");
        logger.indent();
        {
            sources = this.addSources(artifact, logger);
            logger.indent();
            {
                for (final J2clDependency dependency : artifact.dependencies()) {
                    if(false == dependency.isJavascriptSourceRequired()) {
                        continue;
                    }
                    sources.addAll(this.addSources(dependency, logger));
                }
            }
            logger.outdent();
            logger.printEndOfList();
        }
        logger.outdent();

        return ClosureCompiler.compile(request.level,
                request.defines,
                request.entryPoints,
                request.externs,
                request.formatting,
                sources,
                directory.output().append(request.initialScriptFilename.toString()),
                logger) ?
                J2clBuildStepResult.SUCCESS :
                J2clBuildStepResult.FAILED;
    }

    private List<J2clPath> addSources(final J2clDependency artifact,
                                      final J2clLinePrinter logger) {
        logger.printLine(artifact.toString());
        logger.indent();

        final List<J2clPath> sources = Lists.array();
        if(artifact.isProcessingRequired()) {
            final J2clPath transpiled = artifact.step(J2clBuildStep.TRANSPILE).output();
            if (transpiled.exists().isPresent()) {
                sources.add(transpiled);
            }

            // add unpack anyway as it might contain js originally accompanying java source.
            final J2clPath unpack = artifact.step(J2clBuildStep.UNPACK).output();
            if (unpack.exists().isPresent()) {
                sources.add(unpack);
            }

            if (sources.isEmpty()) {
                logger.printLine("No transpiled or unpacked output");
            }
        } else {
            sources.add(artifact.artifactFileOrFail());
        }

        logger.outdent();
        return sources;
    }
}
