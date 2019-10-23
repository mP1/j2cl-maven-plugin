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
abstract class J2clBuildStepWorker {

    final static J2clBuildStepWorker HASH = J2ClBuildStepWorkerHash.instance();

    final static J2clBuildStepWorker UNPACK = J2ClBuildStepWorkerUnpack.instance();

    final static J2clBuildStepWorker COMPILE_SOURCE = J2ClBuildStepWorkerJavacCompilerUnpackedSource.instance();

    final static J2clBuildStepWorker STRIP_GWT_INCOMPAT = J2ClBuildStepWorkerGwtIncompatibleStripPreprocessor.instance();

    final static J2clBuildStepWorker COMPILE_STRIP_GWT_INCOMPAT = J2ClBuildStepWorkerJavacCompilerGwtIncompatibleStrippedSource.instance();

    final static J2clBuildStepWorker TRANSPILER = J2clBiildStepWorkerJ2ClTranspiler.instance();

    final static J2clBuildStepWorker CLOSURE = J2ClBuildStepWorkerClosureCompiler.instance();

    final static J2clBuildStepWorker OUTPUT_ASSEMBLER = J2ClBuildStepWorkerOutputAssembler.instance();

    J2clBuildStepWorker() {
        super();
    }

    abstract J2clBuildStepResult execute(final J2clDependency artifact,
                                         final J2clBuildStep step,
                                         final J2clLinePrinter logger) throws Exception;
}
