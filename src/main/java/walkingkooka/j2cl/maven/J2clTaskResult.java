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

import walkingkooka.NeverError;
import walkingkooka.collect.iterable.Iterables;

import java.io.IOException;
import java.util.Optional;

public enum J2clTaskResult {
    /**
     * Stops processing of remaining tasks.
     */
    ABORTED {
        @Override
        J2clPath path(final J2clTaskDirectory directory) {
            return directory.aborted();
        }

        /**
         * Used by the HASH task, to note the artifact computed hash matches the artifact directory. Try and find the last
         * task directory. If that is FAIL then retry otherwise return the result of task's next as the next.
         */
        @Override
        Optional<J2clTaskKind> next(final J2clArtifact artifact,
                                    final J2clTaskKind current,
                                    final J2clMavenContext context) throws IOException {
            Optional<J2clTaskKind> next = null;

            for (final J2clTaskKind lastToFirst : Iterables.reverse(context.tasks(artifact))) {
                if (current == lastToFirst) {
                    next = Optional.empty();
                    break;
                }
                final J2clTaskDirectory taskDirectory = artifact.taskDirectory(lastToFirst);

                final Optional<J2clTaskResult> maybeResult = taskDirectory.result();
                if (!maybeResult.isPresent()) {
                    continue; // try previous task.
                }

                final J2clTaskResult result = maybeResult.get();
                switch (result) {
                    case ABORTED:
                    case SKIPPED:
                    case SUCCESS:
                        next = result.next(
                                artifact,
                                lastToFirst,
                                context
                        );
                        break;
                    case FAILED:
                        // last completed task FAILED, eg an artifact download failed, retry now
                        taskDirectory.path()
                                .removeAll();
                        next = Optional.of(
                                lastToFirst
                        );
                        break;
                    default:
                        NeverError.unhandledCase(
                                result,
                                J2clTaskResult.values()
                        );
                }

                break;
            }

            return next;
        }
    },
    /**
     * The task failed.
     */
    FAILED {
        @Override
        J2clPath path(final J2clTaskDirectory directory) {
            return directory.failed();
        }

        /**
         * An error occurred, eg a java compile error no point trying further tasks for this dependency.
         */
        @Override
        Optional<J2clTaskKind> next(final J2clArtifact artifact,
                                    final J2clTaskKind current,
                                    final J2clMavenContext context) {
            throw new J2clException(
                    this +
                            " for " +
                            artifact +
                            " failed, refer to log file or console output for more details."
            );
        }
    },
    /**
     * Skips the current task and tries the next.
     */
    SKIPPED {
        @Override
        J2clPath path(final J2clTaskDirectory directory) {
            return directory.skipped();
        }

        @Override
        Optional<J2clTaskKind> next(final J2clArtifact artifact,
                                    final J2clTaskKind current,
                                    final J2clMavenContext context) {
            return context.nextTask(
                    artifact,
                    current
            );
        }
    },
    /**
     * The current task is a success
     */
    SUCCESS {
        @Override
        J2clPath path(final J2clTaskDirectory directory) {
            return directory.successful();
        }

        @Override
        Optional<J2clTaskKind> next(final J2clArtifact artifact,
                                    final J2clTaskKind current,
                                    final J2clMavenContext context) {
            return context.nextTask(
                    artifact,
                    current
            );
        }
    };

    abstract J2clPath path(final J2clTaskDirectory directory);

    /**
     * Only the {@link J2clTaskResult#FAILED} will throw a {@link J2clException}
     */
    final void reportIfFailure(final J2clArtifact artifact,
                               final J2clTaskKind kind) throws J2clException {
        if (FAILED == this) {
            throw new J2clException(kind + " for " + artifact + " failed, refer to log file or console output for more details.");
        }
    }

    abstract Optional<J2clTaskKind> next(final J2clArtifact artifact,
                                         final J2clTaskKind current,
                                         final J2clMavenContext context) throws IOException;
}
