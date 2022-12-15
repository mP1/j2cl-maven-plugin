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

import walkingkooka.j2cl.maven.closure.J2clTaskClosureCompiler;
import walkingkooka.j2cl.maven.hash.J2clTaskHash;
import walkingkooka.j2cl.maven.javac.J2clTaskJavacCompilerGwtIncompatibleStrippedSource;
import walkingkooka.j2cl.maven.javac.J2clTaskJavacCompilerUnpackedSource;
import walkingkooka.j2cl.maven.output.J2clTaskOutputAssembler;
import walkingkooka.j2cl.maven.shade.J2clTaskShadeClassFile;
import walkingkooka.j2cl.maven.shade.J2clTaskShadeJavaSource;
import walkingkooka.j2cl.maven.strip.J2clTaskGwtIncompatibleStripPreprocessor;
import walkingkooka.j2cl.maven.test.J2clTaskWebDriverUnitTestRunner;
import walkingkooka.j2cl.maven.transpile.J2clTask2clTranspiler;
import walkingkooka.j2cl.maven.unpack.J2clTaskUnpack;
import walkingkooka.reflect.PublicStaticHelper;

public final class J2clTasks implements PublicStaticHelper {

    static <C extends J2clMavenContext> J2clTask<C> hash() {
        return J2clTaskHash.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> unpack() {
        return J2clTaskUnpack.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> compileJavaSource() {
        return J2clTaskJavacCompilerUnpackedSource.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> gwtIncompatStrip() {
        return J2clTaskGwtIncompatibleStripPreprocessor.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> compileGwtIncompatStripped() {
        return J2clTaskJavacCompilerGwtIncompatibleStrippedSource.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> shadeJavaSource() {
        return J2clTaskShadeJavaSource.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> shadeClassFiles() {
        return J2clTaskShadeClassFile.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> transpiler() {
        return J2clTask2clTranspiler.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> closure() {
        return J2clTaskClosureCompiler.instance();
    }

    static <C extends J2clMavenContext> J2clTask<C> outputAssembler() {
        return J2clTaskOutputAssembler.instance();
    }

    static J2clTask<J2clMojoTestMavenContext> unitTests() {
        return J2clTaskWebDriverUnitTestRunner.instance();
    }

    private J2clTasks() {
        throw new UnsupportedOperationException();
    }
}
