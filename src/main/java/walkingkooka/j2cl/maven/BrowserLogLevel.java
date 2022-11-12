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

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import walkingkooka.text.CharSequences;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Controls whether which browser console log entries are printed after each test.
 */
enum BrowserLogLevel {
    /**
     * Matches all browser console log entries.
     */
    ALL,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    NONE;

    /**
     * Used to update the capabilities of the provided Driver.
     */
    final void addCapability(final MutableCapabilities capabilities) {
        if(this != NONE) {
            final LoggingPreferences prefs = new LoggingPreferences();
            prefs.enable(LogType.BROWSER, this.toLevel());

            capabilities.setCapability(CapabilityType.LOGGING_PREFS, prefs);
        }
    }

    final Level toLevel() {
        return new Level(this.name(), this.ordinal()) {
            private static final long serialVersionUID = 1L;
        };
    }

    static BrowserLogLevel fromCommandLine(final String level) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(level))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown browser-log-level " + CharSequences.quote(level) + " expected one of " +
                        Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) +
                        "\nhttps://github.com/mP1/README#browser-log-level"));
    }
}
