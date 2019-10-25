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
import java.util.stream.Collectors;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
final class J2clBuildStepWorkerJ2ClTranspiler extends J2ClBuildStepWorker2 {

    /**
     * Singleton
     */
    static J2clBuildStepWorker instance() {
        return new J2clBuildStepWorkerJ2ClTranspiler();
    }

    private J2clBuildStepWorkerJ2ClTranspiler() {
        super();
    }

    @Override
    final J2clBuildStepResult execute0(final J2clDependency artifact,
                                       final J2clStepDirectory directory,
                                       final J2clLinePrinter logger) throws Exception {
        // in the end only the project is compiled, all other dependencies remain untouched.
        final J2clPath sources = artifact.step(artifact.isJreBinary() ?
                J2clBuildStep.UNPACK :
                J2clBuildStep.GWT_INCOMPATIBLE_STRIP)
                .output();
        final List<J2clPath> sourceFiles = sources.findFiles(J2clPath.JAVA_FILES,
                J2clPath.JAVASCRIPT_FILES,
                J2clPath.NATIVE_JAVASCRIPT_FILES);

        logger.print("Preparing...");
        logger.indent();
        logger.printIndented("Sources", sources);
        logger.printIndented("*.java, *.js, *.native.js files", sourceFiles);
        logger.outdent();

        J2clBuildStepResult result;
        if (sourceFiles.size() > 0) {
            final J2clBuildRequest request = artifact.request();

            final List<J2clPath> classpath = Lists.array();
            classpath.addAll(J2clDependency.javacBootstrap().artifactFile().map(Lists::of).orElse(Lists.empty()));
            classpath.addAll(J2clDependency.jre().artifactFile().map(Lists::of).orElse(Lists.empty()));

            classpath.addAll(artifact.dependenciesIncludingTransitives()
                    .stream()
                    .flatMap(d -> d.artifactFile().stream())
                    .collect(Collectors.toList()));

            result = J2clTranspiler.execute(classpath,
                    sourceFiles,
                    directory.output().emptyOrFail(),
                    logger) ?
                    J2clBuildStepResult.SUCCESS :
                    J2clBuildStepResult.FAILED;
        } else {
            logger.printIndentedLine("No files found - transpiling aborted");

            directory.aborted().emptyOrFail();

            result = J2clBuildStepResult.ABORTED;
        }
        return result;
    }
}
