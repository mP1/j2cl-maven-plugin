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
 * Compiles the java source to the target {@link J2clStepDirectory#output()}.
 */
final class J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource extends J2clStepWorkerJavacCompiler {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerJavacCompilerGwtIncompatibleStrippedSource() {
        super();
    }

    @Override
    J2clStep sourcesStep() {
        return J2clStep.GWT_INCOMPATIBLE_STRIP;
    }

    @Override
    boolean shouldRunAnnotationProcessors() {
        return false; // dont need to generate annotation processor classes again.
    }
}