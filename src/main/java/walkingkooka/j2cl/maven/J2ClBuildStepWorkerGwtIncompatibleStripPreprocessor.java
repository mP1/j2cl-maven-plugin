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
final class J2ClBuildStepWorkerGwtIncompatibleStripPreprocessor extends J2ClBuildStepWorker2 {

    /**
     * Singleton
     */
    static J2clBuildStepWorker instance() {
        return new J2ClBuildStepWorkerGwtIncompatibleStripPreprocessor();
    }

    /**
     * Use singleton
     */
    private J2ClBuildStepWorkerGwtIncompatibleStripPreprocessor() {
        super();
    }

    @Override
    J2clBuildStepResult execute0(final J2clDependency artifact,
                                 final J2clStepDirectory directory,
                                 final J2clLinePrinter logger) throws Exception {
        return GwtIncompatibleStripPreprocessor.execute(Lists.of(artifact.step(J2clBuildStep.UNPACK).output()),
                directory.output().emptyOrFail(),
                logger);
    }
}
