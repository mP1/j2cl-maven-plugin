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

import org.junit.Test;
import walkingkooka.j2cl.maven.test.J2clTaskWebDriverUnitTestRunnerBrowser;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2ClTaskWebDriverUnitTestRunnerBrowserTest {

    @Test
    public void testFromUnknown() {
        assertThrows(IllegalArgumentException.class, () -> {
            J2clTaskWebDriverUnitTestRunnerBrowser.fromCommandLine("unknown");
        });
    }

    @Test
    public void testChromeUpperCase() {
        assertSame(J2clTaskWebDriverUnitTestRunnerBrowser.CHROME, J2clTaskWebDriverUnitTestRunnerBrowser.fromCommandLine("CHROME"));
    }

    @Test
    public void testChromeLowerCase() {
        assertSame(J2clTaskWebDriverUnitTestRunnerBrowser.CHROME, J2clTaskWebDriverUnitTestRunnerBrowser.fromCommandLine("chrome"));
    }

    @Test
    public void testHtmlUnit() {
        assertSame(J2clTaskWebDriverUnitTestRunnerBrowser.HTML_UNIT, J2clTaskWebDriverUnitTestRunnerBrowser.fromCommandLine("html_unit"));
    }
}
