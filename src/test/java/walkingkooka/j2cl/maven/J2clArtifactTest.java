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
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;

import java.io.IOException;
import java.util.Collection;

public final class J2clArtifactTest implements ClassTesting2<J2clArtifact> {

    @Test
    public void testIsAnnotationOrInterfaceAnnotation() throws Exception {
        this.isAnnotationAndCheck(SuppressWarnings.class, 2);
    }

    @Test
    public void testIsAnnotationOrInterfaceClass() throws Exception {
        this.isAnnotationAndCheck(Void.class, 0);
    }

    @Test
    public void testIsAnnotationOrInterfaceInterface() throws Exception {
        this.isAnnotationAndCheck(Collection.class, 1);
    }

    @Test
    public void testIsAnnotationOrInterfaceJavaLangObject() throws Exception {
        this.isAnnotationAndCheck(Object.class, 0);
    }

    private void isAnnotationAndCheck(final Class<?> type, final int expected) throws IOException {
        final byte[] classFile = this.getClass()
                .getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")
                .readAllBytes();
        this.checkEquals(expected,
                J2clArtifact.isAnnotationOrInterface(classFile),
                () -> "isAnnotationOrInterface " + type.getName());
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<J2clArtifact> type() {
        return J2clArtifact.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PUBLIC;
    }
}
