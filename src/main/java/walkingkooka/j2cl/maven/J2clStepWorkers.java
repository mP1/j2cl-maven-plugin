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

import walkingkooka.j2cl.maven.closure.J2clStepWorkerClosureCompiler;
import walkingkooka.j2cl.maven.hash.J2clStepWorkerHash;
import walkingkooka.j2cl.maven.javac.J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource;
import walkingkooka.j2cl.maven.javac.J2clStepWorkerJavacCompilerUnpackedSource;
import walkingkooka.j2cl.maven.output.J2clStepWorkerOutputAssembler;
import walkingkooka.j2cl.maven.shade.J2clStepWorkerShadeClassFile;
import walkingkooka.j2cl.maven.shade.J2clStepWorkerShadeJavaSource;
import walkingkooka.j2cl.maven.strip.J2clStepWorkerGwtIncompatibleStripPreprocessor;
import walkingkooka.j2cl.maven.test.J2clStepWorkerWebDriverUnitTestRunner;
import walkingkooka.j2cl.maven.transpile.J2clStepWorkerJ2clTranspiler;
import walkingkooka.j2cl.maven.unpack.J2clStepWorkerUnpack;
import walkingkooka.reflect.PublicStaticHelper;

public final class J2clStepWorkers implements PublicStaticHelper {

    static <C extends J2clMavenContext> J2clStepWorker<C> hash() {
        return J2clStepWorkerHash.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> unpack() {
        return J2clStepWorkerUnpack.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> compileJavaSource() {
        return J2clStepWorkerJavacCompilerUnpackedSource.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> gwtIncompatStrip() {
        return J2clStepWorkerGwtIncompatibleStripPreprocessor.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> compileGwtIncompatStripped() {
        return J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> shadeJavaSource() {
        return J2clStepWorkerShadeJavaSource.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> shadeClassFiles() {
        return J2clStepWorkerShadeClassFile.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> transpiler() {
        return J2clStepWorkerJ2clTranspiler.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> closure() {
        return J2clStepWorkerClosureCompiler.instance();
    }

    static <C extends J2clMavenContext> J2clStepWorker<C> outputAssembler() {
        return J2clStepWorkerOutputAssembler.instance();
    }

    static J2clStepWorker<J2clMojoTestMavenContext> unitTests() {
        return J2clStepWorkerWebDriverUnitTestRunner.instance();
    }

    private J2clStepWorkers() {
        throw new UnsupportedOperationException();
    }
}
