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

import walkingkooka.j2cl.maven.log.TreeLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Represents a single compile task directory. Each and every dependency will have multiple tasks and each task will
 * have its own directory holding a log and possibly other related local files.
 */
public final class J2clTaskDirectory {

    static J2clTaskDirectory with(final Path path) {
        return new J2clTaskDirectory(J2clPath.with(path));
    }

    private J2clTaskDirectory(final J2clPath path) {
        super();
        this.path = path;
    }

    /**
     * Tests if this task directory has a marker directory that indicates it is one of the possible {@link J2clTaskResult}.
     */
    public Optional<J2clTaskResult> result() throws IOException {
        J2clTaskResult result = null;

        if (this.path().exists().isPresent()) {
            for (final J2clTaskResult possible : J2clTaskResult.values()) {
                if (possible.path(this).exists().isPresent()) {
                    result = possible;
                    break;
                }
            }
        }

        return Optional.ofNullable(result);
    }

    /**
     * An abort is not a failure, its simply notes that a task has completed successfully and future tasks need not be executed.
     */
    public J2clPath aborted() {
        return this.path.append("!ABORTED");
    }

    public J2clPath failed() {
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
    public J2clPath hashFile() {
        return this.path.hashFile();
    }

    /**
     * The output directory for the javac compiler, transpiler etc.
     */
    public J2clPath output() {
        return this.path.output();
    }

    public J2clPath skipped() {
        return this.path.append("!SKIPPED");
    }

    public J2clPath successful() {
        return this.path.append("!SUCCESSFUL");
    }

    public J2clPath path() {
        return this.path;
    }

    /**
     * Writes the given lines to a log file under this task directory.
     * Each task is given its own directory and will also have its own local log file showing the output for a particular single task.
     */
    public J2clTaskDirectory writeLog(final List<CharSequence> lines,
                                      final Duration timeTaken,
                                      final TreeLogger logger) throws IOException {
        logger.emptyLine();

        final J2clPath logFile = this.logFile();

        logger.line("Log file");

        logger.indent();
        logger.line(logFile.toString());
        logger.outdent();

        logger.timeTaken(timeTaken);

        Files.write(logFile.path(), lines);
        return this;
    }

    private final J2clPath path;

    @Override
    public String toString() {
        return this.path.toString();
    }
}
