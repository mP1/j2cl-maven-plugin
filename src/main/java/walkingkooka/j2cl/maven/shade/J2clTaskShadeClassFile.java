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

package walkingkooka.j2cl.maven.shade;

import walkingkooka.j2cl.maven.J2clArtifact;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.javashader.JavaShaders;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Scans the output of the previous task for any shade java class files and if any are found writes the result to an output directory.
 */
public final class J2clTaskShadeClassFile<C extends J2clMavenContext> extends J2clTaskShade<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskShadeClassFile<>();
    }

    /**
     * Use singleton
     */
    private J2clTaskShadeClassFile() {
        super();
    }

    @Override
    J2clTaskKind kind() {
        return J2clTaskKind.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE;
    }

    @Override
    Predicate<Path> fileExtensionFilter() {
        return J2clPath.CLASS_FILEEXTENSION;
    }

    @Override
    byte[] shade(final byte[] content,
                 final Map<String, String> mappings) {
        return shadeClassFile(content, mappings);
    }

    private static byte[] shadeClassFile(final byte[] content,
                                         final Map<String, String> mappings) {
        return JavaShaders.classFilePackageShader().apply(content, mappings);
    }

    @Override
    void postCopyAndShade(final J2clArtifact artifact,
                          final J2clPath output,
                          final C context,
                          final TreeLogger logger) {
        // do nothing
    }
}
