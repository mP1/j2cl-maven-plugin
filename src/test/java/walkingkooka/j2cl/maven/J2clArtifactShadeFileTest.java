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

import org.junit.jupiter.api.Test;
import walkingkooka.collect.map.Maps;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.reflect.PackageName;
import walkingkooka.reflect.PublicStaticHelperTesting;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2clArtifactShadeFileTest implements PublicStaticHelperTesting<J2clArtifactShadeFile> {

    @Test
    public void testShadeFileRead() throws Exception {
        this.checkEquals(
                Maps.of(
                        PackageName.with("package1"),
                        PackageName.with("package2")
                ),
                this.readShadeFile("package1=package2")
        );
    }

    @Test
    public void testShadeFileRead2() throws Exception {
        this.checkEquals(
                Maps.of(
                        PackageName.with("package1"),
                        PackageName.with("package2"),
                        PackageName.with("package3"),
                        PackageName.with("package4")
                ),
                this.readShadeFile("package1=package2\npackage3=package4")
        );
    }

    @Test
    public void testShadeFileInvalidPropertyKeyFails() {
        this.readShadeFileFails("*package1=package2");
    }

    @Test
    public void testShadeFileInvalidPropertyKeyFails2() {
        this.readShadeFileFails("package1=package2\n*package3=package4");
    }

    @Test
    public void testShadeFileInvalidPropertyValueFails() {
        this.readShadeFileFails("package1=*package2");
    }

    private Map<PackageName, PackageName> readShadeFile(final String content) throws Exception {
        return J2clArtifactShadeFile.readShadeFile(
                new ByteArrayInputStream(
                        content.getBytes(Charset.defaultCharset())
                )
        );
    }

    private void readShadeFileFails(final String content) {
        assertThrows(
                IllegalStateException.class,
                () -> this.readShadeFile(content)
        );
    }

    // PublicStaticHelperTesting........................................................................................

    @Override
    public Class<J2clArtifactShadeFile> type() {
        return J2clArtifactShadeFile.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PUBLIC;
    }

    @Override
    public boolean canHavePublicTypes(final Method method) {
        return true;
    }
}
