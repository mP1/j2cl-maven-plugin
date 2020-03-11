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

/**
 * Compiles the java source to the target {@link J2clStepDirectory#output()}, with annotation processors enabled.
 */
final class J2clStepWorkerJavacCompilerUnpackedSource extends J2clStepWorkerJavacCompiler {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJavacCompilerUnpackedSource();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerJavacCompilerUnpackedSource() {
        super();
    }

    @Override
    J2clStep sourcesStep() {
        return J2clStep.UNPACK;
    }

    @Override
    J2clStep compiledStep() {
        return J2clStep.COMPILE;
    }

    @Override
    boolean shouldRunAnnotationProcessors() {
        return true;
    }
}
