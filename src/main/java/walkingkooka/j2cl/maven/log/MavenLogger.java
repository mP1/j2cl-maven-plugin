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

import org.apache.maven.plugin.logging.Log;
import walkingkooka.text.Indentation;
import walkingkooka.text.LineEnding;
import walkingkooka.text.printer.IndentingPrinter;
import walkingkooka.text.printer.PrintedLineHandler;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.PrinterException;

import java.util.function.Consumer;

/**
 * Logger interface used by tasks & steps.
 */
public interface MavenLogger {

    /**
     * Default {@link Indentation} shared so all indented printing can have a common indent.
     */
    Indentation INDENTATION = Indentation.SPACES2;

    /**
     * Creates a MAVEN {@link MavenLogger}.
     */
    static MavenLogger maven(final Log log) {
        return BasicMavenLogger.with(log);
    }

    default TreeLogger output(final PrintedLineHandler info,
                              final PrintedLineHandler debug) {
        return TreeLogger.with(
                this.printer(this::info).printedLine(info),
                this.printer(this::debug).printedLine(debug)
        );
    }

    default TreeLogger output() {
        return TreeLogger.with(
                this.printer(this::info),
                null
        );
    }

    /**
     * Returns an {@link IndentingPrinter} which writes to the given {@link Consumer} which is assumed to be a {@link MavenLogger} method.
     */
    default Printer printer(final Consumer<CharSequence> log) {
        return new Printer() {
            @Override
            public void print(final CharSequence text) throws PrinterException {
                log.accept(text);
            }

            @Override
            public LineEnding lineEnding() {
                return LineEnding.SYSTEM;
            }

            @Override
            public void flush() {
                // nop
            }

            @Override
            public void close() {
                // nop
            }

            @Override
            public String toString() {
                return MavenLogger.this.toString();
            }
        }.printedLine((final CharSequence line,
                       final LineEnding lineEnding,
                       final Printer printer) -> printer.print(line));
    }

    void debug(final CharSequence message);

    void debug(final CharSequence message,
               final Throwable cause);

    void info(final CharSequence message);

    void info(final CharSequence message,
              final Throwable cause);

    void warn(final CharSequence message);

    void warn(final CharSequence message,
              final Throwable cause);

    void error(final CharSequence message);

    void error(final CharSequence message,
               final Throwable cause);
}
