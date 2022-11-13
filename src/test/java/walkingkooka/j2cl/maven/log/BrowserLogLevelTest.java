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

import org.junit.jupiter.api.Test;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class BrowserLogLevelTest implements ClassTesting2<BrowserLogLevel> {

    @Test
    public void testFromCommandInvalidFails() {
        final IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> BrowserLogLevel.fromCommandLine("Unknown!")
        );
        this.checkEquals(
                "Unknown browser-log-level \"Unknown!\" expected one of ALL, ERROR, WARN, INFO, DEBUG, NONE\n" +
                        "https://github.com/mP1/j2cl-maven-plugin/README#browser-log-level",
                thrown.getMessage()
        );
    }

    @Test
    public void testFromCommand() {
        for (final BrowserLogLevel level : BrowserLogLevel.values()) {
            assertSame(level, BrowserLogLevel.fromCommandLine(level.name()));
        }
    }

    @Test
    public void testToLevel() {
        for (final BrowserLogLevel level : BrowserLogLevel.values()) {
            this.checkEquals(
                    level.name(),
                    level.toLevel().getName(),
                    () -> "Level " + level
            );
        }
    }

    @Override
    public Class<BrowserLogLevel> type() {
        return BrowserLogLevel.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PUBLIC;
    }
}
