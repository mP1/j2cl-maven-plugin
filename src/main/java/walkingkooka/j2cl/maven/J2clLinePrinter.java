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

import com.google.j2cl.common.FrontendUtils.FileInfo;
import walkingkooka.collect.set.Sets;
import walkingkooka.naming.StringName;
import walkingkooka.naming.StringPath;
import walkingkooka.text.LineEnding;
import walkingkooka.text.pretty.Table;
import walkingkooka.text.pretty.TextPretty;
import walkingkooka.text.pretty.TreePrinting;
import walkingkooka.text.printer.IndentingPrinter;
import walkingkooka.text.printer.Printer;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
                       final Collection<J2clPath> paths) {
        this.printIndentedStringPath(label,
                paths,
                this::toStringPath);
    }

    private StringPath toStringPath(final J2clPath path) {
        return StringPath.parse(path.path().toString().replace(File.separatorChar, '/'));
    }

    void printIndentedFileInfo(final String label,
                               final Collection<FileInfo> paths) {
        this.printIndentedStringPath(label,
                paths,
                this::toStringPath);
    }

    private StringPath toStringPath(final FileInfo fileInfo) {
        final String targetPath = fileInfo.targetPath().replace(File.separatorChar, '/');

        return StringPath.parse(targetPath.startsWith("/") ? targetPath : "/" + targetPath);
    }

    private <T> void printIndentedStringPath(final String label,
                                             final Collection<T> paths,
                                             final Function<T, StringPath> toStringPath) {
        this.printer.lineStart();
        this.printLine(label);
        this.indent();
        this.indent();

        new TreePrinting<StringPath, StringName>() {

            @Override
            public void branchBegin(final List<StringName> names, final IndentingPrinter printer) {
                final String path = this.toPath(names);
                if (false == path.isEmpty()) {
                    J2clLinePrinter.this.printLine(path);
                    printer.indent();
                }
            }

            @Override
            public void branchEnd(final List<StringName> names, final IndentingPrinter printer) {
                final String path = this.toPath(names);
                if (false == path.isEmpty()) {
                    printer.outdent();
                }
            }

            private String toPath(final List<StringName> names) {
                return toPath(names, StringPath.SEPARATOR);
            }

            @Override
            public void children(final Set<StringPath> paths, final IndentingPrinter printer) {
                Table table = TextPretty.table();

                int i = 0;
                for (final StringPath path : paths) {
                    final int column = i % COLUMN_COUNT;
                    final int row = i / COLUMN_COUNT;
                    table = table.setCell(column,
                            row,
                            path.name().toString());

                    i++;
                }

                table = TABLE_TRANSFORMER.apply(table);

                for (int r = 0; r < table.maxRow(); r++) {
                    J2clLinePrinter.this.printLine(TextPretty.rowColumnsToLine((column -> 1), LineEnding.SYSTEM)
                            .apply(table.row(r)));
                }
            }
        }.biConsumer()
                .accept(paths.stream().map(toStringPath).collect(Collectors.toCollection(Sets::sorted)),
                        this.printer);
        this.outdent();
        this.printLine(paths.size() + " file(s)");
        this.outdent();
    }

    private final static int COLUMN_COUNT = 3;

    private final static UnaryOperator<Table> TABLE_TRANSFORMER = TextPretty.tableTransformer(Collections.nCopies(COLUMN_COUNT, TextPretty.columnConfig()
            .minWidth(40)
            .maxWidth(40)
            .overflowMaxWidthBreak()
            .leftAlign()));

    void printIndentedString(final String label,
                             final Collection<String> values) {
        this.printLine(values.size() + " " + label);

        //noinspection ConstantConditions
        if (null != values) {
            this.indent();
            values.forEach(this::printLine);
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
