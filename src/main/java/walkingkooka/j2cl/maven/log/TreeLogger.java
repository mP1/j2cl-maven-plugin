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

import com.google.j2cl.common.SourceUtils.FileInfo;
import walkingkooka.NeverError;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.naming.StringName;
import walkingkooka.naming.StringPath;
import walkingkooka.text.Indentation;
import walkingkooka.text.LineEnding;
import walkingkooka.text.pretty.Table;
import walkingkooka.text.pretty.TextPretty;
import walkingkooka.text.pretty.TreePrinting;
import walkingkooka.text.pretty.TreePrintingBranches;
import walkingkooka.text.printer.IndentingPrinter;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.Printers;

import java.io.File;
import java.nio.file.WatchEvent;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * A logger that contains many print methods to assist with printing lines of text and graphs or tree, with indentation
 * support.
 */
final public class TreeLogger {

    static TreeLogger with(final Consumer<CharSequence> debug,
                           final Consumer<CharSequence> info,
                           final BiConsumer<CharSequence, Throwable> error,
                           final boolean isDebugEnabled) {
        Objects.requireNonNull(debug, "debug");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(error, "error");

        return new TreeLogger(
                debug,
                info,
                error,
                isDebugEnabled
        );
    }

    public void timeTaken(final Duration timeTaken) {
        this.indent();
        {
            this.line("Time Taken");
            this.indentedLine(
                    timeTaken.getSeconds() +
                            "." +
                            timeTaken.getNano() +
                            " seconds"
            );

        }
        this.outdent();

        this.flush();
    }

    private TreeLogger(final Consumer<CharSequence> debug,
                       final Consumer<CharSequence> info,
                       final BiConsumer<CharSequence, Throwable> error,
                       final boolean isDebugEnabled) {
        super();

        this.debug = Printers.sink(LineEnding.SYSTEM)
                .printedLine(
                        (
                                final CharSequence line,
                                final LineEnding lineEnding,
                                final Printer printer) -> TreeLogger.this.debug(line)
                ).indenting(MavenLogger.INDENTATION);
        this.info = Printers.sink(LineEnding.SYSTEM)
                .printedLine(
                        (
                                final CharSequence line,
                                final LineEnding lineEnding,
                                final Printer printer) -> TreeLogger.this.info(line)
                ).indenting(MavenLogger.INDENTATION);
        this.error = error;

        this.debugConsumer = debug;
        this.infoConsumer = info;

        this.isDebugEnabled = isDebugEnabled;
    }

    public void indent() {
        this.debug.indent();
        this.info.indent();
    }

    public void path(final String label,
                     final J2clPath file) {
        if (this.isDebugEnabled) {
            this.line(label);
            this.indentedLine(
                    file.toString()
            );
        }
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
        this.stringPath(
                label,
                paths,
                this::toStringPath,
                format
        );
    }

    private StringPath toStringPath(final J2clPath path) {
        return toStringPath(
                path.path()
                        .toString()
                        .replace(
                                File.separatorChar,
                                '/'
                        )
        );
    }

    /**
     * Note the file paths within the tree will be printed to the second printer.
     */
    public void fileInfos(final String label,
                          final Collection<FileInfo> paths,
                          final TreeFormat format) {
        this.stringPath(
                label,
                paths,
                this::toStringPath,
                format
        );
    }

    private StringPath toStringPath(final FileInfo fileInfo) {
        return toStringPath(
                fileInfo.targetPath()
                        .replace(
                                File.separatorChar,
                                '/'
                        )
        );
    }

    /**
     * Converts the path into a unix style path, adding a leading slash if necessary.
     * The actual value is not used to fetch any path from disk, its only used for sorting and pretty printing.
     */
    private StringPath toStringPath(final String path) {
        return StringPath.parse(
                path.startsWith("/") ?
                        path :
                        "/" + path);
    }

    private <T> void stringPath(final String label,
                                final Collection<T> paths,
                                final Function<T, StringPath> toStringPath,
                                final TreeFormat format) {
        if (this.isDebugEnabled) {
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
    }

    private <T> void stringPath0(final Collection<T> paths,
                                 final Function<T, StringPath> toStringPath,
                                 final TreeFormat format) {
        switch (format) {
            case FLAT:
                flat(paths, toStringPath, this.debug);
                break;
            case TREE:
                tree(paths, toStringPath, this.debug);
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
                .forEach(s -> TreeLogger.line0(s, printer));
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
            public void children(final Set<StringPath> paths,
                                 final IndentingPrinter printer) {
                this.children0(
                        paths.iterator(),
                        printer
                );
            }

            private void children0(final Iterator<StringPath> paths,
                                   final IndentingPrinter printer) {
                // fill rows of text from paths
                final List<List<CharSequence>> allRowsText = Lists.array();

                while (paths.hasNext()) {

                    List<CharSequence> rowText = Lists.array();

                    for (int c = 0; c < COLUMN_COUNT; c++) {
                        if (!paths.hasNext()) {
                            break;
                        }

                        rowText.add(
                                paths.next()
                                        .name()
                                        .toString()
                        );

                        if (!paths.hasNext()) {
                            break;
                        }
                    }

                    allRowsText.add(rowText);
                }

                Table table = TextPretty.table()
                        .setRows(
                                0,
                                0,
                                allRowsText
                        );

                table = TABLE_TRANSFORMER.apply(table);

                for (int r = 0; r < table.height(); r++) {
                    TreeLogger.line0(
                            TextPretty.rowColumnsToLine(
                                    (column -> 1),
                                    LineEnding.SYSTEM
                            ).apply(
                                    table.row(r)
                            ),
                            printer
                    );
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

        if (this.isDebugEnabled) {
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
    }

    public void fileWatchEvents(final List<WatchEvent<?>> events) {
        this.info("File event(s)");
        this.indent();
        {
            for (final WatchEvent<?> event : events) {
                this.line(
                        event.kind()
                                .name()
                                .replace("ENTRY_", "")
                                .toLowerCase() + " " + event.context()
                );
            }
        }
        this.outdent();

        this.flush();
    }

    public synchronized void log(final CharSequence line) {
        this.info.print(line);
    }

    public void debugLine(final CharSequence line) {
        line0(
                line,
                this.debug
        );
    }

    public synchronized void indentedLine(final CharSequence line) {
        this.indent();
        {
            this.line(line);
        }
        this.outdent();
    }

    public void line(final CharSequence line) {
        line0(line, this.info);
    }

    static void line0(final CharSequence line, final IndentingPrinter printer) {
        printer.lineStart();
        printer.print(line);
    }

    public synchronized void emptyLine() {
        this.info.lineStart();
        this.info.print(this.info.lineEnding());
    }

    public synchronized void lineStart() {
        this.info.lineStart();
    }

    public void endOfList() {
        this.line("*** END ***");
    }

    public synchronized void outdent() {
        this.debug.outdent();
        this.info.outdent();
    }

    public synchronized void flush() {
        this.debug.flush();
        this.info.flush();
    }

    private final IndentingPrinter debug;

    private final IndentingPrinter info;


    public synchronized void debug(final CharSequence line) {
        this.debugConsumer.accept(line);
    }

    private final Consumer<CharSequence> debugConsumer;

    public synchronized void info(final CharSequence line) {
        this.infoConsumer.accept(line);
    }

    private final Consumer<CharSequence> infoConsumer;

    public void error(final CharSequence line,
                      final Throwable cause) {
        this.error.accept(line, cause);
    }

    private final BiConsumer<CharSequence, Throwable> error;

    public TreeLogger childTreeLogger(final Consumer<CharSequence> lines,
                                      final BiConsumer<CharSequence, Throwable> error) {
        final Indentation indentation = this.debug.indentation();

        return TreeLogger.with(
                (line) -> {
                    this.debug("" + indentation + MavenLogger.INDENTATION + line);
                    lines.accept(line);
                }, (line) -> {
                    this.info("" + indentation + MavenLogger.INDENTATION + line);
                    lines.accept(line);
                },
                (line, thrown) -> {
                    this.error("" + indentation + MavenLogger.INDENTATION + line, thrown);
                    error.accept(line, thrown);
                },
                this.isDebugEnabled
        );
    }

    public boolean isDebugEnabled() {
        return this.isDebugEnabled;
    }

    private final boolean isDebugEnabled;

    @Override
    public String toString() {
        return this.info.toString();
    }
}
