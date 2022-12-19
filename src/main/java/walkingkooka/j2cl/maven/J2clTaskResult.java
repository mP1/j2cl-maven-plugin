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
         * NOT a fail but skip remaining tasks for this dependency. Used by HASH when the computed hash is the same as previous,
         * and theres no point redoing tasks.
         */
        @Override
        Optional<J2clTaskKind> next(final J2clTaskKind current,
                                    final J2clMavenContext context) {
            return Optional.empty();
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
        Optional<J2clTaskKind> next(final J2clTaskKind current,
                                    final J2clMavenContext context) {
            return Optional.empty();
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
        Optional<J2clTaskKind> next(final J2clTaskKind current,
                                    final J2clMavenContext context) {
            return context.nextTask(current);
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
        Optional<J2clTaskKind> next(final J2clTaskKind current,
                                    final J2clMavenContext context) {
            return context.nextTask(current);
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

    abstract Optional<J2clTaskKind> next(final J2clTaskKind current,
                                         final J2clMavenContext context);
}
