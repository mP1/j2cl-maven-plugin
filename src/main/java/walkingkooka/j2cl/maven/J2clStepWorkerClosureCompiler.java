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

import walkingkooka.collect.map.Maps;

import java.util.Map;

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

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        final J2clRequest request = artifact.request();
        final Map<J2clPath, J2clPathTargetFile> sources;

        logger.printLine("Discovering source roots");
        logger.indent();
        {
            sources = this.addSources(artifact, logger);
            logger.indent();
            {
                for (final J2clDependency dependency : artifact.dependencies()) {
                    if (false == dependency.isJavascriptSourceRequired()) {
                        continue;
                    }
                    sources.putAll(this.addSources(dependency, logger));
                }
            }
            logger.outdent();
            logger.printEndOfList();
        }
        logger.outdent();

        final J2clPath output = directory.output().createIfNecessary();
        logger.printIndented("Output", output);

        return ClosureCompiler.compile(request.level(),
                request.defines(),
                request.entryPoints(),
                request.externs(),
                request.formatting(),
                request.languageOut(),
                request.sourcesKind() == J2clSourcesKind.TEST,
                sources,
                output,
                request.initialScriptFilename().filename(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }

    private Map<J2clPath, J2clPathTargetFile> addSources(final J2clDependency artifact,
                                                         final J2clLinePrinter logger) {
        final Map<J2clPath, J2clPathTargetFile> sources = Maps.sorted();

        logger.printLine(artifact.toString());
        logger.indent();
        {
            final J2clPathTargetFile targetFiles = artifact.isJreJavascriptFiles() ?
                    J2clPathTargetFile.SKIP :
                    J2clPathTargetFile.REPLACE;
            if (artifact.isIgnored()) {
                sources.put(artifact.artifactFileOrFail(), targetFiles);
            } else {
                final J2clPath transpiled = artifact.step(J2clStep.TRANSPILE).output();
                if (transpiled.exists().isPresent()) {
                    sources.put(transpiled, targetFiles);
                }

                // add unpack anyway as it might contain js originally accompanying java source.
                final J2clPath unpack = artifact.step(J2clStep.UNPACK).output();
                if (unpack.exists().isPresent()) {
                    sources.put(unpack, targetFiles);
                }

                if (sources.isEmpty()) {
                    logger.printLine("No transpiled or unpacked output");
                }
            }
        }
        logger.outdent();
        return sources;
    }
}
