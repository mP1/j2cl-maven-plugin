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

import walkingkooka.collect.list.Lists;

/**
 * Compiles the java source to the target {@link J2clStepDirectory#output()}.
 */
final class J2clStepWorkerGwtIncompatibleStripPreprocessor extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerGwtIncompatibleStripPreprocessor();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerGwtIncompatibleStripPreprocessor() {
        super();
    }

    @Override
    J2clStepResult execute1(final J2clDependency artifact,
                            final J2clStepDirectory directory,
                            final J2clLinePrinter logger) throws Exception {
        return GwtIncompatibleStripPreprocessor.execute(Lists.of(artifact.step(J2clStep.JAVAC_COMPILE).output(), artifact.step(J2clStep.UNPACK).output()),
                directory.output().absentOrFail(),
                logger);
    }
}
