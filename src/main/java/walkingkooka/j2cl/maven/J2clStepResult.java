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

public enum J2clStepResult {
    /**
     * Stops processing of remaining steps.
     */
    ABORTED {
        @Override
        J2clPath path(final J2clStepDirectory directory) {
            return directory.aborted();
        }

        @Override
        Optional<J2clStep> next(final Optional<J2clStep> next) {
            return Optional.empty();
        }
    },
    /**
     * The step failed.
     */
    FAILED {
        @Override
        J2clPath path(final J2clStepDirectory directory) {
            return directory.failed();
        }

        @Override
        Optional<J2clStep> next(final Optional<J2clStep> next) {
            return Optional.empty();
        }
    },
    /**
     * Skips the current step and tries the next.
     */
    SKIPPED {
        @Override
        J2clPath path(final J2clStepDirectory directory) {
            return directory.skipped();
        }

        @Override
        Optional<J2clStep> next(final Optional<J2clStep> next) {
            return next;
        }
    },
    /**
     * The current step is a success
     */
    SUCCESS {
        @Override
        J2clPath path(final J2clStepDirectory directory) {
            return directory.successful();
        }

        @Override
        Optional<J2clStep> next(final Optional<J2clStep> next) {
            return next;
        }
    };

    abstract J2clPath path(final J2clStepDirectory directory);

    /**
     * Only the {@link J2clStepResult#FAILED} will throw a {@link J2clException}
     */
    final void reportIfFailure(final J2clDependency dependency,
                               final J2clStep step) throws J2clException {
        if (FAILED == this) {
            throw new J2clException(step + " for " + dependency + " failed, refer to log file or console output for more details.");
        }
    }

    abstract Optional<J2clStep> next(final Optional<J2clStep> next);
}
