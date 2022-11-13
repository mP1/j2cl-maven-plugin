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

package walkingkooka.j2cl.maven.log;

import org.apache.maven.plugin.logging.Log;

/**
 * A {@link J2clLogger} that adapts to a Maven {@link Log}.
 */
final class J2clLoggerMavenLog implements J2clLogger {

    /**
     * {@see J2clLoggerMavenLog}
     */
    static J2clLogger with(final Log log) {
        return new J2clLoggerMavenLog(log);
    }

    private J2clLoggerMavenLog(final Log log) {
        this.log = log;
    }

    @Override
    public void debug(final CharSequence message) {
        this.log.debug(message);
    }

    @Override
    public void debug(final CharSequence message,
                      final Throwable cause) {
        this.log.debug(message, cause);
    }

    @Override
    public void info(final CharSequence message) {
        this.log.info(message);
    }

    @Override
    public void info(final CharSequence message,
                     final Throwable cause) {
        this.log.info(message, cause);
    }

    @Override
    public void warn(final CharSequence message) {
        this.log.warn(message);
    }

    @Override
    public void warn(final CharSequence message,
                     final Throwable cause) {
        this.log.warn(message, cause);
    }

    @Override
    public void error(final CharSequence message) {
        this.log.error(message);
    }

    @Override
    public void error(final CharSequence message,
                      final Throwable cause) {
        this.log.error(message, cause);
    }

    private final Log log;

    @Override
    public String toString() {
        return this.log.toString();
    }
}
