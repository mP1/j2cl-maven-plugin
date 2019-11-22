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
import walkingkooka.text.pretty.TreePrinting;
import walkingkooka.text.printer.IndentingPrinter;
import walkingkooka.text.printer.Printer;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
        this.printLine(paths.size() + " " + label);
        this.indent();

        new TreePrinting<StringPath, StringName>() {

            @Override
            public void branchBegin(final List<StringName> names, final IndentingPrinter printer) {
                final String path = this.path(names);
                if (false == path.isEmpty()) {
                    J2clLinePrinter.this.printLine(path);
                    printer.indent();
                }
            }

            @Override
            public void branchEnd(final List<StringName> names, final IndentingPrinter printer) {
                final String path = this.path(names);
                if (false == path.isEmpty()) {
                    printer.outdent();
                }
            }

            private String path(final List<StringName> names) {
                return names.stream()
                        .filter(n -> false == n.value().isEmpty())
                        .map(StringName::toString)
                        .collect(Collectors.joining("/"));
            }

            @Override
            public void children(final Set<StringPath> paths, final IndentingPrinter printer) {
                for (final StringPath path : paths) {
                    J2clLinePrinter.this.printLine(path.name().value());
                }
            }
        }.biConsumer()
                .accept(paths.stream().map(toStringPath).collect(Collectors.toCollection(Sets::sorted)),
                this.printer);
        this.outdent();
    }

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
