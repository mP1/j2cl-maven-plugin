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
import walkingkooka.NeverError;
import walkingkooka.collect.set.Sets;
import walkingkooka.naming.StringName;
import walkingkooka.naming.StringPath;
import walkingkooka.text.LineEnding;
import walkingkooka.text.pretty.Table;
import walkingkooka.text.pretty.TextPretty;
import walkingkooka.text.pretty.TreePrinting;
import walkingkooka.text.pretty.TreePrintingBranches;
import walkingkooka.text.printer.IndentingPrinter;
import walkingkooka.text.printer.Printer;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

final class J2clLinePrinter {

    static J2clLinePrinter with(final Printer printer,
                                final Printer treePrinter) {
        return new J2clLinePrinter(printer.indenting(J2clLogger.INDENTATION),
                null != treePrinter ? treePrinter.indenting(J2clLogger.INDENTATION) : null,
                null != treePrinter && false == printer.equals(treePrinter));
    }

    private J2clLinePrinter(final IndentingPrinter printer,
                            final IndentingPrinter treePrinter,
                            final boolean indentOutdentBothPrinters) {
        super();

        this.printer = printer;
        this.treePrinter = treePrinter;
        this.indentOutdentBothPrinters = indentOutdentBothPrinters;
    }

    void indent() {
        this.printer.indent();
        if(this.indentOutdentBothPrinters) {
            this.treePrinter.indent();
        }
    }

    void printIndented(final String label,
                       final J2clPath file) {
        this.printLine(label);
        this.printIndentedLine(file.toString());
    }

    /**
     * Note the file paths within the tree will be printed to the second printer.
     */
    void printIndented(final String label,
                       final Collection<J2clPath> paths,
                       final J2clLinePrinterFormat format) {
        this.printIndentedStringPath(label,
                paths,
                this::toStringPath,
                format);
    }

    private StringPath toStringPath(final J2clPath path) {
        return StringPath.parse(path.path().toString().replace(File.separatorChar, '/'));
    }

    /**
     * Note the file paths within the tree will be printed to the second printer.
     */
    void printIndentedFileInfo(final String label,
                               final Collection<FileInfo> paths,
                               final J2clLinePrinterFormat format) {
        this.printIndentedStringPath(label,
                paths,
                this::toStringPath,
                format);
    }

    private StringPath toStringPath(final FileInfo fileInfo) {
        final String targetPath = fileInfo.targetPath().replace(File.separatorChar, '/');

        return StringPath.parse(targetPath.startsWith("/") ? targetPath : "/" + targetPath);
    }

    private <T> void printIndentedStringPath(final String label,
                                             final Collection<T> paths,
                                             final Function<T, StringPath> toStringPath,
                                             final J2clLinePrinterFormat format) {
        this.lineStart();
        if (label.isEmpty()) {
            this.printIndentedStringPath0(paths, toStringPath, format);
        } else {
            this.printLine(label);
            this.lineStart();
            this.indent();
            {
                this.printIndentedStringPath0(paths, toStringPath, format);
            }
            this.outdent();
        }
    }

    private <T> void printIndentedStringPath0(final Collection<T> paths,
                                              final Function<T, StringPath> toStringPath,
                                              final J2clLinePrinterFormat format) {
        this.indent();
        {
//                format.print(paths, toStringPath, this.treePrinter);
            switch (format) {
                case FLAT:
                    printFlat(paths, toStringPath, this.treePrinter);
                    break;
                case TREE:
                    printTree(paths, toStringPath, this.treePrinter);
                    break;
                default:
                    NeverError.unhandledEnum(format, J2clLinePrinterFormat.values());
            }
        }
        this.outdent();
        this.printLine(paths.size() + " file(s)");
    }

    /**
     * Prints a flat listing of paths.
     */
    static <T> void printFlat(final Collection<T> paths,
                              final Function<T, StringPath> toStringPath,
                              final IndentingPrinter printer) {
        paths.stream()
            .map(toStringPath)
            .map(StringPath::toString)
            .forEach(s -> {
                J2clLinePrinter.printLine0(s, printer);
            });
        printer.lineStart();
        printer.flush();
    }
    static <T> void printTree(final Collection<T> paths,
                              final Function<T, StringPath> toStringPath,
                              final IndentingPrinter printer) {
        new TreePrinting<StringPath, StringName>() {

            @Override
            public TreePrintingBranches branches(final StringPath parent) {
                return TreePrintingBranches.SORTED; // the entire tree will be lexical sorted.
            }

            @Override
            public void branchBegin(final List<StringName> names, final IndentingPrinter printer) {
                final String path = this.toPath(names);
                if (false == path.isEmpty()) {
                    J2clLinePrinter.printLine0(path, printer);
                    printer.indent();
                }

                this.level++;
            }

            @Override
            public void branchEnd(final List<StringName> names, final IndentingPrinter printer) {
                final String path = this.toPath(names);
                if (false == path.isEmpty()) {
                    printer.outdent(); // @see branchBegin
                }

                this.level--;
            }

            private String toPath(final List<StringName> names) {
                final String path = toPath(names, StringPath.SEPARATOR);
                return 0 == this.level ?
                        "/" + path :
                        path;
            }

            // https://github.com/mP1/j2cl-maven-plugin/issues/258
            // helpers identify a root path so a leading slash can be added.
            private int level;

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
                    J2clLinePrinter.printLine0(TextPretty.rowColumnsToLine((column -> 1), LineEnding.SYSTEM).apply(table.row(r)), printer);
                }
            }
        }.biConsumer()
                .accept(paths.stream().map(toStringPath).collect(Collectors.toCollection(Sets::sorted)),
                        printer);
        printer.lineStart();
        printer.flush();
    }

    private final static int COLUMN_COUNT = 2;
    private final static int COLUMN_WIDTH = 60;

    private final static UnaryOperator<Table> TABLE_TRANSFORMER = TextPretty.tableTransformer(Collections.nCopies(COLUMN_COUNT, TextPretty.columnConfig()
            .minWidth(COLUMN_WIDTH)
            .maxWidth(COLUMN_WIDTH)
            .overflowMaxWidthBreak()
            .leftAlign()));

    void printIndentedString(final String label,
                             final Collection<String> values) {
        Objects.requireNonNull(values, "values");
        this.printLine(values.size() + " " + label);

        //noinspection ConstantConditions
        if (null != values) {
            this.indent();
            {
                values.forEach(this::printLine);
            }
            this.outdent();
        }
    }

    void print(final CharSequence line) {
        this.printer.print(line);
    }

    void printIndentedLine(final CharSequence line) {
        this.indent();
        {
            this.printLine(line);
        }
        this.outdent();
    }

    void printLine(final CharSequence line) {
        printLine0(line, this.printer);
    }

    static void printLine0(final CharSequence line, final IndentingPrinter printer) {
        printer.lineStart();
        printer.print(line);
    }

    void emptyLine() {
        this.printer.lineStart();
        this.printer.print(this.printer.lineEnding());
    }

    void lineStart() {
        this.printer.lineStart();
    }

    void printEndOfList() {
        this.printLine("*** END ***");
    }

    void outdent() {
        this.printer.outdent();

        if(this.indentOutdentBothPrinters) {
            this.treePrinter.outdent();
        }
    }

    void flush() {
        this.printer.flush();
    }

    private final IndentingPrinter printer;

    private final IndentingPrinter treePrinter;

    private final boolean indentOutdentBothPrinters;

    @Override
    public String toString() {
        return this.printer.toString();
    }
}
