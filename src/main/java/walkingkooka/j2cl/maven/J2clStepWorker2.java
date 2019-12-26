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

import walkingkooka.text.CharSequences;

/**
 * Any step that requires its working directory to be created before it can work.
 */
abstract class J2clStepWorker2 extends J2clStepWorker {

    /**
     * Package private to limit sub classing.
     */
    J2clStepWorker2() {
        super();
    }

    @Override
    final J2clStepResult execute(final J2clDependency artifact,
                                 final J2clStep step,
                                 final J2clLinePrinter logger) throws Exception {
        if(artifact.isExcluded()) {
            throw new IllegalArgumentException("Excluded dependency should not have tasks scheduled: " + CharSequences.quote(artifact.coords().toString()));
        }
        final J2clStepResult result;

        final J2clStepDirectory directory = artifact.step(step);

        logger.printLine("Directory");
        logger.indent();
        {
            logger.printLine(directory.toString());
            logger.indent();
            {
                if (directory.successful().exists().isPresent()) {
                    logger.printIndentedLine("Cache success result present and will be kept");

                    result = J2clStepResult.SUCCESS;
                } else {
                    if (directory.aborted().exists().isPresent()) {
                        logger.printIndentedLine("Cache abort result present and will be kept");

                        result = J2clStepResult.ABORTED;
                    } else {
                        if (directory.skipped().exists().isPresent()) {
                            logger.printIndentedLine("Cache skip result present and will be kept");

                            result = J2clStepResult.SKIPPED;
                        } else {
                            final J2clPath path = directory.path();
                            if (path.exists().isPresent()) {
                                path.removeAll();

                                logger.printIndentedLine("Removed all files");
                            }
                            path.createIfNecessary();

                            result = this.execute0(artifact, directory, logger);
                        }
                    }
                }
            }
            logger.outdent();
        }
        logger.outdent();

        return result;
    }

    private J2clStepResult execute0(final J2clDependency artifact,
                                    final J2clStepDirectory directory,
                                    final J2clLinePrinter logger) throws Exception {
        // aborted steps for the project are transformed into skipped.
        final J2clStepResult result = this.execute1(artifact,
                directory,
                logger);
        return J2clStepResult.ABORTED == result && false == artifact.isDependency() ?
                J2clStepResult.SKIPPED :
                result;
    }

    abstract J2clStepResult execute1(final J2clDependency artifact,
                                     final J2clStepDirectory directory,
                                     final J2clLinePrinter logger) throws Exception;
}
