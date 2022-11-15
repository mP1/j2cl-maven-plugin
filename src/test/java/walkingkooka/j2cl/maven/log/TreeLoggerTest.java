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

package walkingkooka.j2cl.maven.log;

import org.junit.jupiter.api.Test;
import walkingkooka.ToStringTesting;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.text.LineEnding;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.Printers;

import java.nio.file.Paths;
import java.util.List;

public final class TreeLoggerTest implements ClassTesting2<TreeLogger>, ToStringTesting<TreeLogger> {

    private final static LineEnding EOL = LineEnding.NL;

    @Test
    public void testEmptyLine() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.emptyLine();
        logger.flush();

        this.check(EOL, b);
    }

    @Test
    public void testLineStart() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.log("1");
        logger.lineStart();
        logger.log("2");
        logger.flush();

        this.check("1" + EOL + "2", b);
    }

    @Test
    public void testLog() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        final String text = "abc123";
        logger.log(text);

        this.check(text, b);
    }

    @Test
    public void testLine() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        final String text = "abc123";
        logger.line(text);

        this.check(text, b);
    }

    @Test
    public void testLine2() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        final String text1 = "text1";
        logger.line(text1);

        final String text2 = "text2";
        logger.line(text2);

        this.check(text1 + EOL + text2, b);
    }

    @Test
    public void testEndOfLine() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.endOfList();

        this.check("*** END ***", b);
    }

    @Test
    public void testEndOfLine2() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.log("before1");
        logger.endOfList();

        this.check("before1" + EOL + "*** END ***", b);
    }

    @Test
    public void testIndentLineOutdent() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.indent();
        logger.line("line1");
        logger.outdent();
        logger.line("line2");

        this.check("  line1" + EOL + "line2", b);
    }

    @Test
    public void testIndentedLine() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.line("line1");
        logger.indentedLine("line2");
        logger.line("line3");

        this.check("line1" + EOL + "  line2" + EOL + "line3", b);
    }

    @Test
    public void testPath() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.path("label1", path("/path/to"));

        this.check("label1" + EOL + "  /path/to", b);
    }

    @Test
    public void testPathsFlat() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger2(b);

        logger.paths("label1", List.of(path("/path/2"), path("/path/1"), path("/path/3")), TreeFormat.FLAT);

        this.check("label1\n" +
                        "    /path/2<\n" +
                        "    /path/1<\n" +
                        "    /path/3<\n" +
                        "  3 file(s)",
                b);
    }

    @Test
    public void testPathTree() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger2(b);

        logger.paths("label1", List.of(path("/path/to")), TreeFormat.TREE);

        this.check("label1\n" +
                        "    /path<\n" +
                        "      to<\n" +
                        "  1 file(s)",
                b);
    }

    @Test
    public void testPathsTree2() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger2(b);

        logger.paths("label1", List.of(path("/path/to"), path("/path/to2")), TreeFormat.TREE);

        this.check("label1\n" +
                        "    /path<\n" +
                        "      to                                                           to2<\n" +
                        "  2 file(s)",
                b);
    }

    @Test
    public void testStrings() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.strings("label1", List.of("a1"));

        this.check("1 label1\n" +
                        "  a1",
                b);
    }

    @Test
    public void testStrings2() {
        final StringBuilder b = new StringBuilder();
        final TreeLogger logger = this.logger(b);

        logger.strings("label1", List.of("a1", "b2", "c3"));

        this.check("3 label1\n" +
                        "  a1\n" +
                        "  b2\n" +
                        "  c3",
                b);
    }

    private TreeLogger logger(final StringBuilder b) {
        return TreeLogger.with(
                Printers.stringBuilder(b, EOL),
                null
        );
    }

    private TreeLogger logger2(final StringBuilder b) {
        return TreeLogger.with(
                Printers.stringBuilder(b, EOL),
                Printers.stringBuilder(b, EOL)
                        .printedLine((line, lineEnding, logger) -> b.append(line + "<" + lineEnding))
        );
    }

    private void check(final CharSequence expected, final StringBuilder b) {
        this.checkEquals(
                expected.toString(),
                b.toString()
        );
    }

    private J2clPath path(final String path) {
        return J2clPath.with(
                Paths.get(path)
        );
    }

    // toString.........................................................................................................

    @Test
    public void testToString() {
        final Printer logger = Printers.fake();
        this.toStringAndCheck(
                TreeLogger.with(logger, Printers.fake()),
                logger.toString()
        );
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<TreeLogger> type() {
        return TreeLogger.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PUBLIC;
    }
}
