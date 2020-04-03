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
import walkingkooka.ToStringTesting;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.text.LineEnding;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.Printers;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class J2clLinePrinterTest implements ClassTesting2<J2clLinePrinter>, ToStringTesting<J2clLinePrinter> {

    private final static LineEnding EOL = LineEnding.NL;

    @Test
    public void testEmptyLine() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.emptyLine();
        printer.flush();

        this.check(EOL, b);
    }

    @Test
    public void testLineStart() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.print("1");
        printer.lineStart();
        printer.print("2");
        printer.flush();

        this.check("1" + EOL + "2", b);
    }

    @Test
    public void testPrint() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        final String text = "abc123";
        printer.print(text);

        this.check(text, b);
    }

    @Test
    public void testPrintLine() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        final String text = "abc123";
        printer.printLine(text);

        this.check(text, b);
    }

    @Test
    public void testPrintLine2() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        final String text1 = "text1";
        printer.printLine(text1);

        final String text2 = "text2";
        printer.printLine(text2);

        this.check(text1 + EOL + text2, b);
    }

    @Test
    public void testPrintEndOfLine() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printEndOfList();

        this.check("*** END ***", b);
    }

    @Test
    public void testPrintEndOfLine2() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.print("before1");
        printer.printEndOfList();

        this.check("before1" + EOL + "*** END ***", b);
    }

    @Test
    public void testIndentLineOutdent() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.indent();
        printer.printLine("line1");
        printer.outdent();
        printer.printLine("line2");

        this.check("  line1" + EOL + "line2", b);
    }

    @Test
    public void testIndentedLine() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printLine("line1");
        printer.printIndentedLine("line2");
        printer.printLine("line3");

        this.check("line1" + EOL + "  line2" + EOL + "line3", b);
    }

    @Test
    public void testPrintIndentedPath() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printIndented("label1", path("/path/to"));

        this.check("label1" + EOL + "  /path/to", b);
    }

    @Test
    public void testPrintIndentedPathCollection() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printIndented("label1", List.of(path("/path/to")));

        this.check("label1\n" +
                        "    path\n" +
                        "      to\n" +
                        "  1 file(s)",
                b);
    }

    @Test
    public void testPrintIndentedPathCollection2() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printIndented("label1", List.of(path("/path/to"), path("/path/to2")));

        this.check("label1\n" +
                        "    path\n" +
                        "      to                                                           to2\n" +
                        "  2 file(s)",
                b);
    }

    @Test
    public void testPrintIndentedStringCollection() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printIndentedString("label1", List.of("a1"));

        this.check("1 label1\n" +
                        "  a1",
                b);
    }

    @Test
    public void testPrintIndentedStringCollection2() {
        final StringBuilder b = new StringBuilder();
        final J2clLinePrinter printer = this.printer(b);

        printer.printIndentedString("label1", List.of("a1", "b2", "c3"));

        this.check("3 label1\n" +
                        "  a1\n" +
                        "  b2\n" +
                        "  c3",
                b);
    }

    private J2clLinePrinter printer(final StringBuilder b) {
        return J2clLinePrinter.with(Printers.stringBuilder(b, EOL));
    }

    private void check(final CharSequence expected, final StringBuilder b) {
        assertEquals(expected.toString(), b.toString());
    }

    private J2clPath path(final String path) {
        return J2clPath.with(Paths.get(path));
    }

    // toString.........................................................................................................

    @Test
    public void testToString() {
        final Printer printer = Printers.fake();
        this.toStringAndCheck(J2clLinePrinter.with(printer), printer.toString());
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<J2clLinePrinter> type() {
        return J2clLinePrinter.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PACKAGE_PRIVATE;
    }
}
