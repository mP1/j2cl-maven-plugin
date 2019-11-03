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

import com.google.j2cl.common.Problems;
import com.google.j2cl.tools.gwtincompatible.JavaPreprocessor;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Accepts a directory and removes any files marked with @GwtIncompatible.
 * Support is included load an ignore file and skip copying those files only for that directory.
 */
final class GwtIncompatibleStripPreprocessor {

    static J2clBuildStepResult execute(final Collection<J2clPath> sourceRoots,
                                       final J2clPath output,
                                       final J2clLinePrinter logger) throws IOException {
        output.exists()
                .orElseThrow(() -> new IllegalArgumentException("Output not a directory or does not exist: " + CharSequences.quote(output.toString())));

        return execute0(new TreeSet<>(sourceRoots), output, logger);
    }

    private static J2clBuildStepResult execute0(final SortedSet<J2clPath> sourceRoots,
                                                final J2clPath output,
                                                final J2clLinePrinter logger) throws IOException {
        J2clBuildStepResult result;

        if (sourceRoots.isEmpty()) {
            result = J2clBuildStepResult.ABORTED;
        } else {
            final int filteredFileCount;
            final List<J2clPath> filteredFiles = Lists.array();

            logger.printLine("Copy *.java, *.js, *.native.js");
            logger.indent();
            {
                logger.printLine("Output");
                logger.printIndentedLine(output.toString());

                logger.printLine(sourceRoots.size() + " Source(s)");
                logger.indent();
                {
                    for (final J2clPath root : sourceRoots) {
                        logger.printLine(root.toString());
                        logger.indent();

                        filteredFiles.addAll(walkTreeThenCopy(root, output, logger));

                        logger.outdent();
                    }
                }
                logger.outdent();


                filteredFileCount = filteredFiles.size();
                logger.printLine(filteredFileCount + " file(s) count");
            }
            logger.outdent();

            if (filteredFileCount > 0) {
                result = processFiles(filteredFiles, output, logger);
            } else {
                logger.printIndentedLine("No files found - stripping aborted");

                output.removeAll(); // dont want to leave empty output directory when its empty.
                result = J2clBuildStepResult.ABORTED;
            }
        }

        return result;
    }

    /**
     * Gather files that match *.java, *.js, *.native.js for the given root(src) then copy them over to the destination,
     * but never overwriting the file. This honours the classpath concept that preceding files take precedence over later
     * files with the same relative to a root path.
     */
    private static List<J2clPath> walkTreeThenCopy(final J2clPath src,
                                                   final J2clPath dest,
                                                   final J2clLinePrinter logger) throws IOException {
        final SortedSet<J2clPath> files = gatherFiles(src);
        return dest.copyFiles(src, files, logger::printLine);
    }

    /**
     * Finds all files under the root that match *.java, *.js or *.native.js collecting their paths into a {@link java.util.SortedSet}.
     * and honours any .j2cl-maven-plugin-ignore.txt files that it may find.
     */
    private static SortedSet<J2clPath> gatherFiles(final J2clPath root) throws IOException {
        final SortedSet<J2clPath> files = Sets.sorted();

        final Map<Path, List<PathMatcher>> pathToMatchers = Maps.hash();
        final List<PathMatcher> exclude = Lists.array();
        final List<PathMatcher> include = Lists.of(J2clPath.JAVA_FILES, J2clPath.JAVASCRIPT_FILES, J2clPath.NATIVE_JAVASCRIPT_FILES);

        Files.walkFileTree(root.path(), new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                                                     final BasicFileAttributes attrs) throws IOException {
                final J2clPath ignoreFile = J2clPath.with(dir).append(".j2cl-maven-plugin-ignore.txt");
                if (ignoreFile.exists().isPresent()) {
                    final List<PathMatcher> matchers = Files.readAllLines(ignoreFile.path())
                            .stream()
                            .filter(l -> false == l.startsWith("#") | l.trim().length() > 0)
                            .map(l -> FileSystems.getDefault().getPathMatcher("glob:" + dir + File.separator + l))
                            .collect(Collectors.toList());
                    pathToMatchers.put(dir, matchers);
                    exclude.addAll(matchers);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir,
                                                      final IOException cause) {
                final List<PathMatcher> matchers = pathToMatchers.remove(dir);
                if (null != matchers) {
                    matchers.forEach(exclude::remove);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes basicFileAttributes) {
                if (exclude.stream().noneMatch(m -> m.matches(file))) {
                    if (include.stream()
                            .anyMatch(m -> m.matches(file))) {
                        files.add(J2clPath.with(file));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    private static J2clBuildStepResult processFiles(final List<J2clPath> files,
                                                    final J2clPath output,
                                                    final J2clLinePrinter logger) {
        J2clBuildStepResult result;

        logger.printLine("JavaPreprocessor");
        {
            logger.indent();
            {
                logger.printIndented("Output", output);

                final Problems problems = new Problems();
                JavaPreprocessor.preprocessFiles(J2clPath.toFileInfo(files),
                        output.path(),
                        problems);
                final List<String> errors = problems.getErrors();
                final int errorCount = errors.size();

                logger.printLine(errorCount + " Error(s)");
                logger.indent();

                {
                    if (errorCount > 0) {
                        logger.indent();
                        errors.forEach(logger::printLine);
                        logger.outdent();

                        logger.printEndOfList();
                        result = J2clBuildStepResult.FAILED;
                    } else {
                        result = J2clBuildStepResult.SUCCESS;
                    }
                    logger.printEndOfList();
                }
                logger.outdent();
            }
            logger.outdent();
        }

        return result;
    }
}
