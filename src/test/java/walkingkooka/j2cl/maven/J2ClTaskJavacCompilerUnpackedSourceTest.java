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
import walkingkooka.j2cl.maven.javac.J2clTaskJavacCompilerUnpackedSource;
import walkingkooka.test.Testing;

public final class J2ClTaskJavacCompilerUnpackedSourceTest implements Testing {

    // // 'javatests.org.gwtproject.timer.client.TimerJ2clTest_AdapterSuite' -> org.gwtproject.timer.client.TimerJ2clTest
    @Test
    public void testExtractTestClassName() {
        final String testClassName = "javatests.org.gwtproject.timer.client.TimerJ2clTest_AdapterSuite";
        this.checkEquals("org.gwtproject.timer.client.TimerJ2clTest",
                J2clTaskJavacCompilerUnpackedSource.extractTestClassName(testClassName),
                () -> "J2clTaskJavacCompilerUnpackedSource.extractTestClassName " + testClassName
        );
    }
}
