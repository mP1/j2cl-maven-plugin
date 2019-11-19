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

import walkingkooka.text.printer.IndentingPrinter;
import walkingkooka.text.printer.Printer;

import java.util.Collection;
import java.util.function.Function;

final class J2clLinePrinter {

    static J2clLinePrinter with(final Printer printer) {
        return new J2clLinePrinter(printer.indenting(J2clLogger.INDENTATION));
    }

    private J2clLinePrinter(final IndentingPrinter printer) {
        super();

        this.printer = printer;
    }

    void indent() {
        this.printer.indent();
    }

    void printIndented(final String label,
                       final J2clPath file) {
        this.printLine(label);
        this.printIndentedLine(file.toString());
    }

    void printIndented(final String label,
                       final Collection<?> values) {
        this.printIndented(label, values, Object::toString);
    }

    <T> void printIndented(final String label,
                           final Collection<T> values,
                           final Function<T, String> formatter) {
        this.printLine(values.size() + " " + label);

        //noinspection ConstantConditions
        if (null != values) {
            this.indent();
            values.forEach(f -> this.printLine(formatter.apply(f)));
            this.outdent();
        }
    }

    void print(final CharSequence line) {
        this.printer.print(line);
    }

    void printIndentedLine(final CharSequence line) {
        this.indent();
        this.printLine(line);
        this.outdent();
    }

    void printLine(final CharSequence line) {
        this.printer.lineStart();
        this.printer.print(line);
    }

    void emptyLine() {
        this.printer.lineStart();
        this.printer.print(this.printer.lineEnding());
    }

    void printEndOfList() {
        this.printLine("*** END ***");
    }

    void outdent() {
        this.printer.outdent();
    }

    void flush() {
        this.printer.flush();
    }

    private final IndentingPrinter printer;

    @Override
    public String toString() {
        return this.printer.toString();
    }
}
