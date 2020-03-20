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

import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import walkingkooka.HashCodeEqualsDefinedTesting2;
import walkingkooka.text.CharSequences;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class J2clPathTest implements HashCodeEqualsDefinedTesting2<J2clPath> {

    @Rule
    public TemporaryFolder base = new TemporaryFolder();

    @BeforeEach
    public void beforeEach() throws IOException {
        this.base.create();
    }

    @AfterEach
    public void afterEach() throws IOException {
        this.base.delete();
    }

    @Test
    public void testTestAdapterSuiteGeneratedFilename() {
        final J2clPath output = this.output();
        final String testClassName = "org.gwtproject.timer.client.TimerJ2clTest";
        assertEquals(output.append("/org/gwtproject/timer/client/TimerJ2clTest.testsuite"),
                output.testAdapterSuiteGeneratedFilename(testClassName),
                () -> output + ".testAdapterSuiteGeneratedFilename " + CharSequences.quote(testClassName));
    }

    @Test
    public void testTestAdapterSuiteCorrectFilename() {
        final J2clPath output = this.output();
        final String testClassName = "org.gwtproject.timer.client.TimerJ2clTest";
        assertEquals(output.append("/javatests/org/gwtproject/timer/client/TimerJ2clTest_AdapterSuite.js"),
                output.testAdapterSuiteCorrectFilename(testClassName),
                () -> output + ".testAdapterSuiteCorrectFilename " + CharSequences.quote(testClassName));
    }

    // equals...........................................................................................................

    @Test
    public void testDifferent() {
        this.checkNotEquals(this.output());
    }

    // HashCodeEqualsDefinedTesting2....................................................................................

    @Override
    public J2clPath createObject() {
        return J2clPath.with(this.base.getRoot().toPath());
    }

    private J2clPath output() {
        return J2clPath.with(this.base.getRoot().toPath()).output();
    }
}
