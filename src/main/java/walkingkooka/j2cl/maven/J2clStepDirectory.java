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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Represents a single compile step directory. Each and every dependency will have multiple steps and each step will
 * have its own directory holding a log and possibly other related local files.
 */
final class J2clStepDirectory {

    static J2clStepDirectory with(final Path path) {
        return new J2clStepDirectory(J2clPath.with(path));
    }

    private J2clStepDirectory(final J2clPath path) {
        super();
        this.path = path;
    }

    /**
     * An abort is not a failure, its simply notes that a step has completed successfully and future steps need not be executed.
     */
    J2clPath aborted() {
        return this.path.append("!ABORTED");
    }

    J2clPath failed() {
        return this.path.append("!FAILED");
    }

    /**
     * The path to the log file in this directory.
     */
    private J2clPath logFile() {
        return this.path.append("log.txt");
    }

    /**
     * The file that will capture the components of a hashing.
     */
    J2clPath hashFile() {
        return this.path.hashFile();
    }

    /**
     * The output directory for the javac compiler, transpiler etc.
     */
    J2clPath output() {
        return this.path.output();
    }

    J2clPath skipped() {
        return this.path.append("!SKIPPED");
    }

    J2clPath successful() {
        return this.path.append("!SUCCESSFUL");
    }

    J2clPath path() {
        return this.path;
    }

    /**
     * Writes the given lines to a log file under this job step directory.
     * Each step is given its own directory and will also have its own local log file showing the output for a particular single step.
     */
    J2clStepDirectory writeLog(final List<CharSequence> lines,
                               final J2clLinePrinter logger) throws IOException {
        logger.emptyLine();

        final J2clPath logFile = this.logFile();

        logger.printLine("Log file");

        logger.indent();
        logger.printLine(logFile.toString());
        logger.outdent();

        logger.emptyLine();
        logger.emptyLine();
        logger.flush();

        Files.write(logFile.path(), lines);
        return this;
    }

    private final J2clPath path;

    @Override
    public String toString() {
        return this.path.toString();
    }
}
