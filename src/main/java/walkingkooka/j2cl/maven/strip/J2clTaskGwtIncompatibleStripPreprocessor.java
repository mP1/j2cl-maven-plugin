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
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeLogger;

/**
 * Compiles the java source to the target {@link J2clTaskDirectory#output()}.
 */
public final class J2clTaskGwtIncompatibleStripPreprocessor<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskGwtIncompatibleStripPreprocessor<>();
    }

    /**
     * Use singleton
     */
    private J2clTaskGwtIncompatibleStripPreprocessor() {
        super();
    }

    @Override
    public J2clTaskResult execute(final J2clDependency artifact,
                                  final J2clTaskKind kind,
                                  final C context,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                kind,
                context,
                logger
        );
    }

    @Override
    public J2clTaskResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clTaskDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        return GwtIncompatibleStripPreprocessor.execute(
                Lists.of(
                        artifact.task(J2clTaskKind.JAVAC_COMPILE).output(),
                        artifact.task(J2clTaskKind.UNPACK).output()
                ),
                directory.output().absentOrFail(),
                logger
        );
    }
}
