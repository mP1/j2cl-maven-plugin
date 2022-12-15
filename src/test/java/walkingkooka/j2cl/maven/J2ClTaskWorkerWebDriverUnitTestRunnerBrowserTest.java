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
import walkingkooka.j2cl.maven.test.J2clTaskWorkerWebDriverUnitTestRunnerBrowser;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2ClTaskWorkerWebDriverUnitTestRunnerBrowserTest {

    @Test
    public void testFromUnknown() {
        assertThrows(IllegalArgumentException.class, () -> {
            J2clTaskWorkerWebDriverUnitTestRunnerBrowser.fromCommandLine("unknown");
        });
    }

    @Test
    public void testChromeUpperCase() {
        assertSame(J2clTaskWorkerWebDriverUnitTestRunnerBrowser.CHROME, J2clTaskWorkerWebDriverUnitTestRunnerBrowser.fromCommandLine("CHROME"));
    }

    @Test
    public void testChromeLowerCase() {
        assertSame(J2clTaskWorkerWebDriverUnitTestRunnerBrowser.CHROME, J2clTaskWorkerWebDriverUnitTestRunnerBrowser.fromCommandLine("chrome"));
    }

    @Test
    public void testHtmlUnit() {
        assertSame(J2clTaskWorkerWebDriverUnitTestRunnerBrowser.HTML_UNIT, J2clTaskWorkerWebDriverUnitTestRunnerBrowser.fromCommandLine("html_unit"));
    }
}
