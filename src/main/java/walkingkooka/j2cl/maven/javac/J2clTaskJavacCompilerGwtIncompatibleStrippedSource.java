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

package walkingkooka.j2cl.maven.javac;

import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clArtifact;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;

/**
 * Compiles the java source to the target {@link J2clTaskDirectory#output()}.
 */
public final class J2clTaskJavacCompilerGwtIncompatibleStrippedSource<C extends J2clMavenContext> extends J2clTaskJavacCompiler<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskJavacCompilerGwtIncompatibleStrippedSource<>();
    }

    /**
     * Use singleton
     */
    private J2clTaskJavacCompilerGwtIncompatibleStrippedSource() {
        super();
    }

    @Override
    J2clTaskKind sourceTask() {
        return J2clTaskKind.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE;
    }

    @Override
    List<J2clTaskKind> compiledBinaryTasks() {
        return Lists.of(
                J2clTaskKind.SHADE_CLASS_FILES,
                J2clTaskKind.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE
        );
    }

    @Override
    boolean shouldRunAnnotationProcessors() {
        return false; // dont need to generate annotation processor classes again.
    }

    /**
     * Try adding the SHADED_CLASS_FILES then COMPILE_GWT_INCOMPATIBLE_STRIPPED then default to the original archive file.
     */
    @Override
    J2clPath selectClassFiles(final J2clArtifact artifact) {
        return artifact.sourcesOrArchiveFile(this.compiledBinaryTasks());
    }

    @Override
    void postCompile(final J2clArtifact artifact,
                     final J2clTaskDirectory directory,
                     final C context,
                     final TreeLogger logger) {
        // nop
    }
}
