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

import org.junit.jupiter.api.Test;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.text.CharSequences;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2clClasspathScopeTest implements ClassTesting2<J2clClasspathScope> {

    @Test
    public void testCommandLineOptionNullFails() {
        assertThrows(NullPointerException.class, () ->
                J2clClasspathScope.commandLineOption(null));
    }

    @Test
    public void testCommandLineOptionEmptyFails() {
        this.commandLineOptionFails("");
    }

    @Test
    public void testCommandLineOptionUnknownFails() {
        this.commandLineOptionFails("?Unknown");
    }

    private void commandLineOptionFails(final String text) {
        assertThrows(IllegalArgumentException.class, () ->
                J2clClasspathScope.commandLineOption(text));
    }

    @Test
    public void testCompile() {
        this.commandLineOptionAndCheck("compile", J2clClasspathScope.COMPILE);
    }

    @Test
    public void testCompileRuntime() {
        this.commandLineOptionAndCheck("compile+runtime", J2clClasspathScope.COMPILE_PLUS_RUNTIME);
    }

    @Test
    public void testTest() {
        this.commandLineOptionAndCheck("test", J2clClasspathScope.TEST);
    }

    @Test
    public void testRuntime() {
        this.commandLineOptionAndCheck("runtime", J2clClasspathScope.RUNTIME);
    }

    @Test
    public void testRuntimeSystem() {
        this.commandLineOptionAndCheck("runtime+system", J2clClasspathScope.RUNTIME_PLUS_SYSTEM);
    }

    @Test
    public void testProvided() {
        this.commandLineOptionAndCheck("provided", J2clClasspathScope.PROVIDED);
    }

    @Test
    public void testSystem() {
        this.commandLineOptionAndCheck("system", J2clClasspathScope.SYSTEM);
    }

    @Test
    public void testImport() {
        this.commandLineOptionAndCheck("import", J2clClasspathScope.IMPORT);
    }

    private void commandLineOptionAndCheck(final String text,
                                           final J2clClasspathScope expected) {
        assertSame(expected,
                J2clClasspathScope.commandLineOption(text),
                () -> "commandLineOption " + CharSequences.quoteAndEscape(text));
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<J2clClasspathScope> type() {
        return J2clClasspathScope.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PACKAGE_PRIVATE;
    }
}
