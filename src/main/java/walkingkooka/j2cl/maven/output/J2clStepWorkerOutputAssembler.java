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

package walkingkooka.j2cl.maven.output;

import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepDirectory;
import walkingkooka.j2cl.maven.J2clStepResult;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.Collection;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
public final class J2clStepWorkerOutputAssembler implements J2clStepWorker {

    /**
     * Singleton
     */
    public static J2clStepWorker instance() {
        return new J2clStepWorkerOutputAssembler();
    }

    private J2clStepWorkerOutputAssembler() {
        super();
    }

    @Override
    public J2clStepResult execute(final J2clDependency artifact,
                                  final J2clStep step,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                step,
                logger
        );
    }

    @Override
    public J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clStepDirectory directory,
                                               final TreeLogger logger) throws Exception {
        final J2clPath source = artifact.step(J2clStep.CLOSURE_COMPILE).output();
        logger.path("Source", source);

        final J2clPath target = artifact.context().target();
        logger.path("Destination", target);
        target.createIfNecessary();

        final Collection<J2clPath> files = source.gatherFiles(J2clPath.ALL_FILES);

        final J2clStepResult result;

        if (target.copyFiles(source,
                files,
                J2clPath.COPY_FILE_CONTENT_VERBATIM,
                logger).isEmpty()) {
            logger.line("No files copied, transpile step likely failed with warnings that are actually errors.");
            result = J2clStepResult.FAILED;
        } else {
            result = J2clStepResult.SUCCESS;
        }

        return result;
    }
}
