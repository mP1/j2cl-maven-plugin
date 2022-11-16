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

package walkingkooka.j2cl.maven.strip;

import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepDirectory;
import walkingkooka.j2cl.maven.J2clStepResult;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;

/**
 * Compiles the java source to the target {@link J2clStepDirectory#output()}.
 */
public final class J2clStepWorkerGwtIncompatibleStripPreprocessor implements J2clStepWorker {

    /**
     * Singleton
     */
    public static J2clStepWorker instance() {
        return new J2clStepWorkerGwtIncompatibleStripPreprocessor();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerGwtIncompatibleStripPreprocessor() {
        super();
    }

    @Override
    public J2clStepResult execute(final J2clDependency artifact,
                                  final J2clStep step,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                step,
                logger
        );
    }

    @Override
    public J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clStepDirectory directory,
                                               final TreeLogger logger) throws Exception {
        return GwtIncompatibleStripPreprocessor.execute(
                Lists.of(
                        artifact.step(J2clStep.JAVAC_COMPILE).output(),
                        artifact.step(J2clStep.UNPACK).output()
                ),
                directory.output().absentOrFail(),
                logger
        );
    }
}
