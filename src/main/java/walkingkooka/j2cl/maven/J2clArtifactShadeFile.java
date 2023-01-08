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

import walkingkooka.collect.map.Maps;
import walkingkooka.reflect.PackageName;
import walkingkooka.reflect.PublicStaticHelper;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public final class J2clArtifactShadeFile implements PublicStaticHelper {

    /**
     * Reads a text file holding mappings of package name to package name, skipping comments and blank lines.
     */
    public static Map<PackageName, PackageName> readShadeFile(final InputStream content) throws IOException {
        try (content) {
            final Map<PackageName, PackageName> map = Maps.ordered();
            final Properties properties = new Properties() {

                private static final long serialVersionUID = -7831642683636345017L;

                // not necessary to override setProperty but just in case future load call setProperty and not put

                @Override
                public synchronized Object setProperty(final String key, final String value) {
                    return map.put(
                            this.checkPackage(key, "key"),
                            this.checkPackage(value, "value")
                    );
                }

                private PackageName checkPackage(final String value,
                                                 final String label) {
                    try {
                        return PackageName.with(value);
                    } catch (final Exception cause) {
                        throw new IllegalStateException("Invalid property " + label + " (package name) " + CharSequences.quoteAndEscape(value));
                    }
                }

                @Override
                public synchronized Object put(final Object key, final Object value) {
                    return this.setProperty((String) key, (String) value);
                }
            };
            properties.load(content);
            return map;
        }
    }

    private J2clArtifactShadeFile() {
        throw new UnsupportedOperationException();
    }
}
