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

import com.google.j2cl.common.FrontendUtils.FileInfo;
import walkingkooka.NeverError;
import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.J2clPath;
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
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * A logger that contains many print methods to assist with printing lines of text and graphs or tree, with indentation
 * support.
 */
final public class TreeLogger {

    static TreeLogger with(final Printer printer,
                           final Printer treePrinter) {
        return new TreeLogger(
                printer.indenting(MavenLogger.INDENTATION),
                null != treePrinter ? treePrinter.indenting(MavenLogger.INDENTATION) : null,
                null != treePrinter && false == printer.equals(treePrinter)
        );
    }

    public static String prettyTimeTaken(final Duration timeTaken) {
        return timeTaken.getSeconds() +
                "." +
                timeTaken.getNano() +
                " seconds";
    }

    private TreeLogger(final IndentingPrinter printer,
                       final IndentingPrinter treePrinter,
                       final boolean indentOutdentBothPrinters) {
        super();

        this.printer = printer;
        this.treePrinter = treePrinter;
        this.indentOutdentBothPrinters = indentOutdentBothPrinters;
    }

    public void indent() {
        this.printer.indent();

        if (this.indentOutdentBothPrinters) {
            this.treePrinter.indent();
        }
    }

    public void path(final String label,
                     final J2clPath file) {
        this.line(label);
        this.indentedLine(
                file.toString()
        );
    }

    public void path(final J2clPath file) {
        this.line(
                file.toString()
        );
    }

    /**
     * Note the file paths within the tree will be printed to the second printer.
     */
    public void paths(final String label,
                      final Collection<J2clPath> paths,
                      final TreeFormat format) {
        this.stringPath(label,
                paths,
                this::toStringPath,
                format);
    }

    private StringPath toStringPath(final J2clPath path) {
        return StringPath.parse(
                path.path()
                        .toString()
                        .replace(File.separatorChar, '/')
        );
    }

    /**
     * Note the file paths within the tree will be printed to the second printer.
     */
    public void fileInfos(final String label,
                          final Collection<FileInfo> paths,
                          final TreeFormat format) {
        this.stringPath(label,
                paths,
                this::toStringPath,
                format);
    }

    private StringPath toStringPath(final FileInfo fileInfo) {
        final String targetPath = fileInfo.targetPath().replace(File.separatorChar, '/');

        return StringPath.parse(targetPath.startsWith("/") ? targetPath : "/" + targetPath);
    }

    private <T> void stringPath(final String label,
                                final Collection<T> paths,
                                final Function<T, StringPath> toStringPath,
                                final TreeFormat format) {
        this.lineStart();
        if (label.isEmpty()) {
            this.stringPath0(paths, toStringPath, format);
        } else {
            this.line(label);
            this.lineStart();
            this.indent();
            {
                this.stringPath0(paths, toStringPath, format);
            }
            this.outdent();
        }
    }

    private <T> void stringPath0(final Collection<T> paths,
                                 final Function<T, StringPath> toStringPath,
                                 final TreeFormat format) {
        switch (format) {
            case FLAT:
                flat(paths, toStringPath, this.treePrinter);
                break;
            case TREE:
                tree(paths, toStringPath, this.treePrinter);
                break;
            default:
                NeverError.unhandledEnum(format, TreeFormat.values());
        }
    }

    /**
     * Prints a flat listing of paths.
     */
    static <T> void flat(final Collection<T> paths,
                         final Function<T, StringPath> toStringPath,
                         final IndentingPrinter printer) {
        paths.stream()
                .map(toStringPath)
                .map(StringPath::toString)
                .forEach(s -> {
                    TreeLogger.line0(s, printer);
                });
        printer.lineStart();
        printer.flush();
    }

    static <T> void tree(final Collection<T> paths,
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
                    TreeLogger.line0(path, printer);
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
                    TreeLogger.line0(TextPretty.rowColumnsToLine((column -> 1), LineEnding.SYSTEM).apply(table.row(r)), printer);
                }
            }
        }.biConsumer()
                .accept(paths.stream()
                                .map(toStringPath)
                                .collect(Collectors.toCollection(Sets::ordered)),
                        printer);
        printer.lineStart();
        printer.flush();
    }

    private final static int COLUMN_COUNT = 2;
    private final static int COLUMN_WIDTH = 60;

    private final static UnaryOperator<Table> TABLE_TRANSFORMER = TextPretty.tableTransformer(
            Collections.nCopies(
                    COLUMN_COUNT,
                    TextPretty.columnConfig()
                            .minWidth(COLUMN_WIDTH)
                            .maxWidth(COLUMN_WIDTH)
                            .overflowMaxWidthBreak()
                            .leftAlign())
    );

    public void strings(final String label,
                        final Collection<String> values) {
        Objects.requireNonNull(values, "values");
        this.line(values.size() + " " + label);

        //noinspection ConstantConditions
        if (null != values) {
            this.indent();
            {
                values.forEach(this::line);
            }
            this.outdent();
        }
    }

    public void log(final CharSequence line) {
        this.printer.print(line);
    }

    public void indentedLine(final CharSequence line) {
        this.indent();
        {
            this.line(line);
        }
        this.outdent();
    }

    public void line(final CharSequence line) {
        line0(line, this.printer);
    }

    static void line0(final CharSequence line, final IndentingPrinter printer) {
        printer.lineStart();
        printer.print(line);
    }

    public void emptyLine() {
        this.printer.lineStart();
        this.printer.print(this.printer.lineEnding());
    }

    public void lineStart() {
        this.printer.lineStart();
    }

    public void endOfList() {
        this.line("*** END ***");
    }

    public void outdent() {
        this.printer.outdent();

        if (this.indentOutdentBothPrinters) {
            this.treePrinter.outdent();
        }
    }

    public void flush() {
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
