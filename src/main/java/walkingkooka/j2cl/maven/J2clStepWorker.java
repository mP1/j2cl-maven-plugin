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

@SuppressWarnings("StaticInitializerReferencesSubClass")
abstract class J2clStepWorker {

    final static J2clStepWorker HASH = J2ClStepWorkerHash.instance();

    final static J2clStepWorker UNPACK = J2ClStepWorkerUnpack.instance();

    final static J2clStepWorker COMPILE_SOURCE = J2ClStepWorkerJavacCompilerUnpackedSource.instance();

    final static J2clStepWorker STRIP_GWT_INCOMPAT = J2ClStepWorkerGwtIncompatibleStripPreprocessor.instance();

    final static J2clStepWorker COMPILE_STRIP_GWT_INCOMPAT = J2ClStepWorkerJavacCompilerGwtIncompatibleStrippedSource.instance();

    final static J2clStepWorker TRANSPILER = J2clStepWorkerJ2ClTranspiler.instance();

    final static J2clStepWorker CLOSURE = J2ClStepWorkerClosureCompiler.instance();

    final static J2clStepWorker OUTPUT_ASSEMBLER = J2ClStepWorkerOutputAssembler.instance();

    J2clStepWorker() {
        super();
    }

    abstract J2clStepResult execute(final J2clDependency artifact,
                                    final J2clStep step,
                                    final J2clLinePrinter logger) throws Exception;
}
