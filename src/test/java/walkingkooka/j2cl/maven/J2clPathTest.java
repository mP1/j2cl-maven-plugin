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
import walkingkooka.HashCodeEqualsDefinedTesting2;
import walkingkooka.compare.ComparableTesting2;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2clPathTest implements ComparableTesting2<J2clPath>, HashCodeEqualsDefinedTesting2<J2clPath> {

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
    public void testAsbentOrFailEmpty() throws IOException {
        final J2clPath path = this.createObject();
        final J2clPath absent = path.append("absent1");
        assertSame(absent, absent.absentOrFail());
    }

    @Test
    public void testAbsentOrFailEmptyNotEmpty() throws IOException {
        final J2clPath path = this.createObject();
        path.append("sub").createIfNecessary();

        assertThrows(IllegalArgumentException.class, () -> {
            path.absentOrFail();
        });
    }

    @Test
    public void testAppendWithoutCreating() {
        final J2clPath path = this.createObject();
        final J2clPath append = path.append("append1");
        this.absentCheck(append);
        this.checkPath(append, path.toString() + File.separator + "append1");
    }

    @Test
    public void testCompareLess() {
        this.compareToAndCheckLess(J2clPath.with(Paths.get("/a/b")), J2clPath.with(Paths.get("/z")));
    }

    @Test
    public void testCreateIfNecessary() throws IOException {
        final J2clPath path = this.createObject();
        final J2clPath sub = path.append("sub1");
        this.absentCheck(sub);
        assertSame(sub, sub.createIfNecessary());
        this.existsCheck(sub);
    }

    @Test
    public void testExists() {
        this.existsCheck(this.createObject());
    }

    @Test
    public void testFilename() throws IOException {
        final J2clPath path = this.createObject();
        final String filename = "file1";
        assertEquals(filename, path.append(filename).writeFile(new byte[0]).filename());
    }

    @Test
    public void testFile() throws IOException {
        final File file = this.base.newFile();
        assertEquals(file, J2clPath.with(file.toPath()).file());
    }

    @Test
    public void testIgnoreFile() {
        final J2clPath path = this.createObject();
        this.checkPath(path.ignoreFile(), path + File.separator + ".walkingkooka-j2cl-maven-plugin-ignore.txt");
    }

    @Test
    public void testIsFileExists() throws IOException {
        final J2clPath path = this.createObject();
        final String filename = "abc.txt";
        this.base.newFile(filename);
        this.isFileAndCheck(path.append(filename), true);
    }

    @Test
    public void testIsFileAbsent() {
        final J2clPath path = this.createObject();
        final String filename = "abc.txt";
        this.isFileAndCheck(path.append(filename), false);
    }

    @Test
    public void testIsFileDirectory() throws IOException {
        final J2clPath path = this.createObject();
        final String directory = "directory1";
        this.isFileAndCheck(path.append(directory).createIfNecessary(), false);
    }

    private void isFileAndCheck(final J2clPath path, final boolean expected) {
        assertEquals(expected, path.isFile(), () -> path.toString());
    }

    @Test
    public void testIsJavaExists() throws IOException {
        final J2clPath path = this.createObject();
        final String filename = "Class.java";
        this.base.newFile(filename);
        this.isJavaAndCheck(path.append(filename), true);
    }

    @Test
    public void testIsJavaDifferent() throws IOException {
        final J2clPath path = this.createObject();
        final String filename = "abc.txt";
        this.base.newFile(filename);
        this.isJavaAndCheck(path.append(filename), false);
    }

    @Test
    public void testIsJavaAbsent() {
        final J2clPath path = this.createObject();
        final String filename = "Class.java";
        this.isJavaAndCheck(path.append(filename), true);
    }

    @Test
    public void testIsJavaDirectory() throws IOException {
        final J2clPath path = this.createObject();
        final String directory = "directory1";
        this.isJavaAndCheck(path.append(directory).createIfNecessary(), false);
    }

    private void isJavaAndCheck(final J2clPath path, final boolean expected) {
        assertEquals(expected, path.isJava(), () -> path.toString());
    }

    @Test
    public void testOutput() {
        final J2clPath path = this.createObject();
        this.checkPath(path.output(), path + File.separator + "output");
    }

    @Test
    public void testRemoveAll() throws IOException {
        final J2clPath path = this.createObject();

        final J2clPath dir = path.append("dir1");
        dir.createIfNecessary();

        final J2clPath file = dir.append("file2");
        file.writeFile(new byte[]{1, 2, 3});

        path.removeAll();

        this.absentCheck(file);
        this.absentCheck(dir);
        this.absentCheck(path);
    }

    @Test
    public void testShadeFile() {
        final J2clPath path = this.createObject();
        this.checkPath(path.shadeFile(), path + File.separator + ".walkingkooka-j2cl-maven-plugin-shade.txt");
    }

    @Test
    public void testTestAdapterSuiteGeneratedFilename() {
        final J2clPath output = this.output();
        final String testClassName = "org.gwtproject.timer.client.TimerJ2clTest";
        assertEquals(output.append("/org/gwtproject/timer/client/TimerJ2clTest.testsuite"),
                output.testAdapterSuiteGeneratedFilename(testClassName),
                () -> output + ".testAdapterSuiteGeneratedFilename " + CharSequences.quote(testClassName));
    }

    @Test
    public void testTestAdapterSuiteCorrectFilename() {
        final J2clPath output = this.output();
        final String testClassName = "org.gwtproject.timer.client.TimerJ2clTest";
        assertEquals(output.append("/javatests/org/gwtproject/timer/client/TimerJ2clTest_AdapterSuite.js"),
                output.testAdapterSuiteCorrectFilename(testClassName),
                () -> output + ".testAdapterSuiteCorrectFilename " + CharSequences.quote(testClassName));
    }

    private void existsCheck(final J2clPath path) {
        assertEquals(Optional.of(path), path.exists(), "path " + path);
    }

    private void absentCheck(final J2clPath path) {
        assertEquals(Optional.empty(), path.exists(), "path " + path);
    }

    private void checkPath(final J2clPath j2clPath, final String path) {
        assertEquals(path, j2clPath.path().toString());
        assertEquals(path, j2clPath.toString());
    }

    // equals...........................................................................................................

    @Test
    public void testDifferent() {
        this.checkNotEquals(this.output());
    }

    // HashCodeEqualsDefinedTesting2....................................................................................

    @Override
    public J2clPath createObject() {
        return J2clPath.with(this.base.getRoot().toPath());
    }

    private J2clPath output() {
        return J2clPath.with(this.base.getRoot().toPath()).output();
    }

    // ComparableTesting................................................................................................

    @Override
    public J2clPath createComparable() {
        return this.createObject();
    }
}
