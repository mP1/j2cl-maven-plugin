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

import java.util.Collection;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
final class J2clStepWorkerOutputAssembler extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerOutputAssembler();
    }

    private J2clStepWorkerOutputAssembler() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        final J2clPath source = artifact.step(J2clStep.CLOSURE_COMPILER).output();
        logger.printIndented("Source", source);

        final J2clPath target = artifact.request().target();
        logger.printIndented("Destination", target);
        target.createIfNecessary();

        final Collection<J2clPath> files = source.gatherFiles(J2clPath.ALL_FILES);

        final J2clStepResult result;

        if (target.copyFiles(source, files, logger).isEmpty()) {
            logger.printLine("No files copied, transpile step likely failed with warnings that are actually errors.");
            result = J2clStepResult.FAILED;
        } else {
            result = J2clStepResult.SUCCESS;
        }

        return result;
    }
}
