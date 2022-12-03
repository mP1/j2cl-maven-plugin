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
import walkingkooka.text.Indentation;

/**
 * Logger interface used by tasks & steps.
 */
public interface MavenLogger {

    /**
     * Default {@link Indentation} shared so all indented printing can have a common indent.
     */
    Indentation INDENTATION = Indentation.SPACES2;

    /**
     * Creates a MAVEN {@link MavenLogger}.
     */
    static MavenLogger maven(final Log log) {
        return BasicMavenLogger.with(log);
    }

    boolean isDebugEnabled();

    void debug(final CharSequence message);

    void debug(final CharSequence message,
               final Throwable cause);

    void info(final CharSequence message);

    void info(final CharSequence message,
              final Throwable cause);

    void warn(final CharSequence message);

    void warn(final CharSequence message,
              final Throwable cause);

    void error(final CharSequence message);

    void error(final CharSequence message,
               final Throwable cause);

    default TreeLogger treeLogger() {
        return TreeLogger.with(
                this::debug,
                this::info,
                this::error,
                this.isDebugEnabled()
        );
    }
}
