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

import org.junit.Test;
import walkingkooka.Cast;
import walkingkooka.collect.map.Maps;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class J2clStepWorkerShadeClassFileTest {

    @Test
    public void testUnchangedEmptyShadings() throws Exception {
        final TestClass testClass = this.shadeAndLoad(TestClass.class, Maps.empty());

        assertEquals("field1", testClass.field, "field1");
        assertEquals("method2", testClass.method(), "method2");
        assertSame(testClass, testClass.returnsThis(), "returnsThis");
    }

    @Test
    public void testUnchangedDifferentShadings() throws Exception {
        final TestClass testClass = this.shadeAndLoad(TestClass.class, Maps.of("different", "different2"));

        assertEquals("field1", testClass.field, "field1");
        assertEquals("method2", testClass.method(), "method2");
        assertSame(testClass, testClass.returnsThis(), "returnsThis");
    }

    @Test
    public void testShaded() throws Exception {
        final String from = TestClass.class.getName();
        final String shaded = "shaded." + from;

        final Object loaded = this.shadeAndLoad(from, shaded, Maps.of(from, shaded));
        final Class<?> loadedType = loaded.getClass();

        assertEquals("shaded type name", shaded, loaded.getClass().getName());
        assertEquals("field1", loadedType.getField("field").get(loaded), "field1");
        assertEquals("method2", loadedType.getMethod("method").invoke(loaded), "method2");
        assertEquals("returnsThis", loaded, loadedType.getMethod("returnsThis").invoke(loaded));
    }

    // helpers..........................................................................................................

    private <T> T shadeAndLoad(final Class<T> type,
                               final Map<String, String> shadings) throws Exception {
        return this.shadeAndLoad(type.getName(), shadings);
    }

    private <T> T shadeAndLoad(final String classFileType,
                               final Map<String, String> shadings) throws Exception {
        return this.shadeAndLoad(classFileType, classFileType, shadings);
    }

    private <T> T shadeAndLoad(final String classFileTypeName,
                               final String loadTypeName,
                               final Map<String, String> shadings) throws Exception {
        final byte[] file = this.loadClassFile(classFileTypeName);
        final ByteClassLoader classLoader = new ByteClassLoader();
        classLoader.setClass(loadTypeName, J2clStepWorkerShadeClassFile.shadeClassFile(file, shadings));
        final Class<T> klass = Cast.to(classLoader.loadClass(loadTypeName));
        return klass.getDeclaredConstructor().newInstance();
    }

    private static String classFileResourceName(final String typeName) {
        return "/" + typeName.replace('.', '/') + ".class";
    }

    private byte[] loadClassFile(final String typeName) throws IOException {
        final String resourceName = classFileResourceName(typeName);

        try (final InputStream file = this.getClass().getResourceAsStream(resourceName)) {
            assertNotNull("Class file for " + typeName + " resource=" + resourceName + " not found", file);
            return file.readAllBytes();
        }
    }

    static class ByteClassLoader extends ClassLoader {

        ByteClassLoader() {
            super();
        }

        void setClass(final String typeName,
                      final byte[] classFile) {
            this.typeName = typeName;
            this.classFile = classFile;
        }

        @Override
        protected Class<?> findClass(final String className) throws ClassNotFoundException {
            if (false == className.equals(this.typeName)) {
                throw new ClassNotFoundException("Unknown class " + className);
            }

            return defineClass(className, this.classFile, 0, this.classFile.length);
        }

        private String typeName;
        private byte[] classFile;
    }

    public static class TestClass {

        public String field = "field1";

        public Object method() {
            return "method2";
        }

        public TestClass returnsThis() {
            return this;
        }
    }
}
