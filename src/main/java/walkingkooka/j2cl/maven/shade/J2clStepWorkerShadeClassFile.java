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

import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.javashader.JavaShaders;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Scans the output of the previous step for any shade java class files and if any are found writes the result to an output directory.
 */
public final class J2clStepWorkerShadeClassFile extends J2clStepWorkerShade {

    /**
     * Singleton
     */
    public static J2clStepWorker instance() {
        return new J2clStepWorkerShadeClassFile();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerShadeClassFile() {
        super();
    }

    @Override
    J2clStep step() {
        return J2clStep.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE;
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
    void postCopyAndShade(final J2clDependency artifact,
                          final J2clPath output,
                          final TreeLogger logger) {
        // do nothing
    }
}
