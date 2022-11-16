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

package walkingkooka.j2cl.maven.closure;

import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ClosureFormattingOptionTest {

    @Test
    public void testFromUnknown() {
        assertThrows(IllegalArgumentException.class, () -> {
            ClosureFormattingOption.fromCommandLine("unknown");
        });
    }

    @Test
    public void testPrettyPrintUpperCase() {
        assertSame(ClosureFormattingOption.PRETTY_PRINT, ClosureFormattingOption.fromCommandLine("PRETTY_PRINT"));
    }

    // http://googleclosure.blogspot.com/2010/10/pretty-print-javascript-with-closure.html
    @Test
    public void testPrettyPrintLowerCase() {
        assertSame(ClosureFormattingOption.PRETTY_PRINT, ClosureFormattingOption.fromCommandLine("pretty_print"));
    }

    @Test
    public void testSingleQuotesLowerCase() {
        assertSame(ClosureFormattingOption.SINGLE_QUOTES, ClosureFormattingOption.fromCommandLine("single_quotes"));
    }
}
