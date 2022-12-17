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

import walkingkooka.j2cl.maven.log.TreeLogger;

@SuppressWarnings("StaticInitializerReferencesSubClass")
public interface J2clTask<C extends J2clMavenContext> {

    J2clTaskResult execute(final J2clDependency artifact,
                           final J2clTaskKind kind,
                           final C context,
                           final TreeLogger logger) throws Exception;

    default J2clTaskResult executeIfNecessary(final J2clDependency artifact,
                                              final J2clTaskKind kind,
                                              final C context,
                                              final TreeLogger logger) throws Exception {
        final J2clTaskResult result;

        final J2clTaskDirectory directory = artifact.taskDirectory(kind);

        logger.line("Directory");
        logger.indent();
        {
            logger.line(directory.toString());
            logger.indent();
            {
                if (context.shouldCheckCache()) {
                    result = executeIfNecessary0(
                            artifact,
                            directory,
                            context,
                            logger
                    );
                } else {
                    final J2clPath path = directory.path();
                    if (path.exists().isPresent()) {
                        path.removeAll();

                        logger.indentedLine("Removed all files");
                    }
                    path.createIfNecessary();

                    // aborted tasks for the project are transformed into skipped.
                    final J2clTaskResult nextResult = this.executeWithDirectory(
                            artifact,
                            directory,
                            context,
                            logger
                    );
                    result = nextResult;
                }
            }
            logger.outdent();
        }
        logger.outdent();

        return result;
    }

    private J2clTaskResult executeIfNecessary0(final J2clDependency artifact,
                                               final J2clTaskDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        final J2clTaskResult result;
        if (directory.successful().exists().isPresent()) {
            logger.indentedLine("Cache success result present and will be kept");

            result = J2clTaskResult.SUCCESS;
        } else {
            if (directory.aborted().exists().isPresent()) {
                logger.indentedLine("Cache abort result present and will be kept");

                result = J2clTaskResult.ABORTED;
            } else {
                if (directory.skipped().exists().isPresent()) {
                    logger.indentedLine("Cache skip result present and will be kept");

                    result = J2clTaskResult.SKIPPED;
                } else {
                    final J2clPath path = directory.path();
                    if (path.exists().isPresent()) {
                        path.removeAll();

                        logger.indentedLine("Removed all files");
                    }
                    path.createIfNecessary();

                    // aborted tasks for the project are transformed into skipped.
                    final J2clTaskResult nextResult = this.executeWithDirectory(
                            artifact,
                            directory,
                            context,
                            logger
                    );
                    result = J2clTaskResult.ABORTED == nextResult && false == artifact.isDependency() ?
                            J2clTaskResult.SKIPPED :
                            nextResult;
                }
            }
        }
        return result;
    }

    J2clTaskResult executeWithDirectory(final J2clDependency artifact,
                                        final J2clTaskDirectory directory,
                                        final C context,
                                        final TreeLogger logger) throws Exception;
}
