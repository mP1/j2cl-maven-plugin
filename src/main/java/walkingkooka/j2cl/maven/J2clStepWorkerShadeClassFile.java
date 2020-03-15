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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import walkingkooka.util.SystemProperty;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;

/**
 * Scans the output of the previous step for any shade java class files and if any are found writes the result to an output directory.
 */
final class J2clStepWorkerShadeClassFile extends J2clStepWorkerShade {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
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
        return J2clStep.COMPILE_GWT_INCOMPATIBLE_STRIPPED;
    }

    @Override
    BiPredicate<Path, BasicFileAttributes> fileFilter() {
        return J2clPath.CLASS_FILES;
    }

    @Override
    byte[] shade(final byte[] content,
                 final Map<String, String> mappings) {
        return shadeClassFile(content, mappings);
    }

    // @VisibleForTesting
    static byte[] shadeClassFile(final byte[] content,
                                 final Map<String, String> mappings) {
        final ClassReader reader = new ClassReader(content);
        final ClassWriter writer = new ClassWriter(0);
        final ClassRemapper adapter = new ClassRemapper(writer, new Remapper() {
            @Override
            public String map(final String typeName) {
                String result = null;

                for (final Entry<String, String> possible : mappings.entrySet()) {
                    final String from = possible.getKey();
                    if (typeName.startsWith(binaryTypeName(from))) {
                        result = typeName.replace(binaryTypeName(from), binaryTypeName(possible.getValue()));
                        break;
                    }
                }

                return null != result ?
                        result :
                        super.map(typeName);
            }
        });
        reader.accept(adapter, ClassReader.EXPAND_FRAMES);

        writer.visitEnd();

        return writer.toByteArray();
    }

    private static String binaryTypeName(final String typeName) {
        return typeName.replace('.', '/');
    }
}
