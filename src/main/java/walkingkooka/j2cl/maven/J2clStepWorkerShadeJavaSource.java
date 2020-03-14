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

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;

/**
 * If the dependency source has a shade file, create an output directory with selected shaded class files combined
 * with the other class files changed.
 */
final class J2clStepWorkerShadeJavaSource extends J2clStepWorkerShade {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerShadeJavaSource();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerShadeJavaSource() {
        super();
    }

    @Override
    J2clStep step() {
        return J2clStep.GWT_INCOMPATIBLE_STRIP;
    }

    @Override
    BiPredicate<Path, BasicFileAttributes> fileFilter() {
        return J2clPath.JAVA_FILES;
    }

    @Override
    byte[] shade(final byte[] content,
                 final Map<String, String> shadings) {
        final Charset charset = Charset.defaultCharset();
        String text = new String(content, charset);

        // TODO nasty simply remove the package prefix, replace with javaparser that transforms java source imports, fqcns etc.
        for (final Entry<String, String> mapping : shadings.entrySet()) {
            text = text.replace(mapping.getKey(), mapping.getValue());
        }

        return text.getBytes(charset);
    }
}
