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

import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import walkingkooka.ToStringTesting;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HashBuilderTest implements ClassTesting2<HashBuilder>, ToStringTesting<HashBuilder> {

    @Rule
    public TemporaryFolder base = new TemporaryFolder();

    @BeforeEach
    public void beforeEach() throws IOException {
        this.base.create();
    }

    @AfterEach
    public void afterEach() {
        this.base.delete();
    }

    @Test
    public void testHashBytes() {
        this.checkNotEquals(HashBuilder.empty()
                        .append(new byte[1])
                        .append(new byte[]{2, 3})
                        .toString(),
                HashBuilder.empty()
                        .append(new byte[]{2, 3})
                        .append(new byte[1])
                        .toString());
    }

    @Test
    public void testHashBytesRepeated() {
        this.checkEquals(HashBuilder.empty()
                        .append(new byte[]{1, 2})
                        .append(new byte[]{3, 4})
                        .toString(),
                HashBuilder.empty()
                        .append(new byte[]{1, 2})
                        .append(new byte[]{3, 4})
                        .toString());
    }

    @Test
    public void testHashEnum() {
        this.checkNotEquals(HashBuilder.empty()
                        .append(RoundingMode.CEILING)
                        .append(RoundingMode.FLOOR)
                        .toString(),
                HashBuilder.empty()
                        .append(RoundingMode.FLOOR)
                        .append(RoundingMode.CEILING)
                        .toString());
    }

    @Test
    public void testHashEnumRepeated() {
        this.checkEquals(HashBuilder.empty()
                        .append(RoundingMode.CEILING)
                        .append(RoundingMode.FLOOR)
                        .toString(),
                HashBuilder.empty()
                        .append(RoundingMode.CEILING)
                        .append(RoundingMode.FLOOR)
                        .toString());
    }

    @Test
    public void testHashPath() throws IOException {
        final Path path1 = this.base.newFile().toPath();
        final byte[] content1 = "abc".getBytes(Charset.defaultCharset());
        Files.write(path1, content1);

        final Path path2 = this.base.newFile().toPath();
        final byte[] content2 = "def".getBytes(Charset.defaultCharset());
        Files.write(path2, content2);

        this.checkNotEquals(HashBuilder.empty()
                        .append(path1)
                        .append(path2)
                        .toString(),
                HashBuilder.empty()
                        .append(path2)
                        .append(path1)
                        .toString());
    }

    @Test
    public void testHashPathRepeated() throws IOException {
        final Path path1 = this.base.newFile().toPath();
        final byte[] content1 = "abc".getBytes(Charset.defaultCharset());
        Files.write(path1, content1);

        final Path path2 = this.base.newFile().toPath();
        final byte[] content2 = "def".getBytes(Charset.defaultCharset());
        Files.write(path2, content2);

        this.checkEquals(HashBuilder.empty()
                        .append(path1)
                        .append(path2)
                        .toString(),
                HashBuilder.empty()
                        .append(path1)
                        .append(path2)
                        .toString());
    }

    @Test
    public void testHashPathVsString() throws IOException {
        final Path path = this.base.newFile().toPath();
        final byte[] content = "abc".getBytes(Charset.defaultCharset());
        Files.write(path, content);

        this.checkEquals(HashBuilder.empty()
                        .append(path)
                        .toString(),
                HashBuilder.empty()
                        .append("abc")
                        .toString());
    }

    @Test
    public void testHashString() {
        this.checkNotEquals(HashBuilder.empty()
                        .append("A")
                        .append("BC")
                        .toString(),
                HashBuilder.empty()
                        .append("BC")
                        .append("A")
                        .toString());
    }

    @Test
    public void testHashStringRepeated() {
        this.checkEquals(HashBuilder.empty()
                        .append("A")
                        .append("BC")
                        .toString(),
                HashBuilder.empty()
                        .append("A")
                        .append("BC")
                        .toString());
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<HashBuilder> type() {
        return HashBuilder.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PACKAGE_PRIVATE;
    }
}
