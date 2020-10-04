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
import com.google.j2cl.common.Problems;
import com.google.j2cl.tools.gwtincompatible.GwtIncompatibleStripper;
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
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Accepts a directory and removes any files marked with @GwtIncompatible.
 * Support is included to load an ignore file and skip copying those files only for that directory.
 */
final class GwtIncompatibleStripPreprocessor {

    static J2clStepResult execute(final List<J2clPath> sourceRoots,
                                  final J2clPath output,
                                  final J2clLinePrinter logger) throws IOException {
        output.exists()
                .orElseThrow(() -> new IllegalArgumentException("Output not a directory or does not exist: " + CharSequences.quote(output.toString())));

        J2clStepResult result;

        // FileInfo must have the source path otherwise stripper will write files to the wrong place.
        final List<FileInfo> javaFiles = prepareJavaFiles(sourceRoots, output, logger);

        final int javaFileCount = javaFiles.size();

        if (javaFileCount > 0) {
            result = processStripAnnotationsFiles(javaFiles, output, logger);

            copyJavascriptFiles(sourceRoots, output, logger);
            logger.printIndented("Output file(s)", output.gatherFiles(J2clPath.ALL_FILES), J2clLinePrinterFormat.TREE);

        } else {
            logger.printIndentedLine("No files found");

            output.removeAll(); // dont want to leave empty output directory when its empty.
            result = J2clStepResult.ABORTED;
        }

        return result;
    }

    private static List<FileInfo> prepareJavaFiles(final List<J2clPath> sourceRoots,
                                                   final J2clPath output,
                                                   final J2clLinePrinter logger) throws IOException {
        final List<FileInfo> javaFiles = Lists.array();

        logger.printLine("Preparing java files");
        logger.indent();
        {
            logger.indent();
            {
                for (final J2clPath sourceRoot : sourceRoots) {
                    final Set<J2clPath> copied = gatherFiles(sourceRoot, J2clPath.JAVA_FILES);
                    // find then copy from unpack to $output
                    final Collection<J2clPath> files = output.copyFiles(sourceRoot,
                            copied,
                            J2clPath.COPY_FILE_CONTENT_VERBATIM,
                            logger);

                    // necessary to prepare FileInfo with correct sourceRoot otherwise stripped files will be written back to the wrong place.
                    javaFiles.addAll(J2clPath.toFileInfo(files, output));
                }
            }
            logger.outdent();
            logger.printLine(javaFiles.size() + " file(s)");
        }
        logger.outdent();

        return javaFiles;
    }

    /**
     * Invokes the java preprocesor which use annotations to discover classes, methods and fields to remove from the actual source files.
     * Because the source files are modified a previous step will have taken copied and place them in this output ready for modification if necessary.
     * Errors will also be logged.
     */
    private static J2clStepResult processStripAnnotationsFiles(final List<FileInfo> javaFilesInput,
                                                               final J2clPath output,
                                                               final J2clLinePrinter logger) {
        J2clStepResult result;

        logger.printLine("GwtIncompatibleStripper");
        {
            logger.indent();
            {
                logger.printIndentedFileInfo("Source(s)", javaFilesInput, J2clLinePrinterFormat.TREE);
                logger.printIndented("Output", output);

                final Problems problems = new Problems();
                GwtIncompatibleStripper.preprocessFiles(javaFilesInput,
                        output.path(),
                        problems);
                logger.printIndentedString("Message(s)", problems.getMessages());

                final List<String> errors = problems.getErrors();
                logger.printIndentedString("Error(s)", errors);
                logger.printIndentedString("Warning(s)", problems.getWarnings());

                result = errors.isEmpty() ?
                        J2clStepResult.SUCCESS :
                        J2clStepResult.FAILED;
            }
            logger.outdent();
        }

        return result;
    }

    private static void copyJavascriptFiles(final List<J2clPath> sourceRoots,
                                            final J2clPath output,
                                            final J2clLinePrinter logger) throws IOException {
        logger.printLine("Copy *.js from source root(s) to output");
        logger.indent();
        {
            for (final J2clPath sourceRoot : sourceRoots) {
                final Set<J2clPath> copy = gatherFiles(sourceRoot, J2clPath.JAVASCRIPT_FILES);
                output.copyFiles(sourceRoot,
                        copy,
                        J2clPath.COPY_FILE_CONTENT_VERBATIM,
                        logger);
            }
        }
        logger.outdent();
    }

    /**
     * Finds all files under the root that match the given {@link BiPredicate} collecting their paths into a {@link SortedSet}.
     * and honours any ignore files if any found.
     */
    private static Set<J2clPath> gatherFiles(final J2clPath root,
                                             final Predicate<Path> include) throws IOException {

        return root.exists().isPresent() ?
                gatherFiles0(root, include) :
                Sets.empty();
    }

    private static Set<J2clPath> gatherFiles0(final J2clPath root,
                                              final Predicate<Path> include) throws IOException {
        final SortedSet<J2clPath> files = Sets.sorted();

        final Map<Path, List<PathMatcher>> pathToMatchers = Maps.hash();
        final List<PathMatcher> exclude = Lists.array();

        Files.walkFileTree(root.path(), new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                                                     final BasicFileAttributes attrs) throws IOException {
                final J2clPath ignoreFile = J2clPath.with(dir).ignoredFiles();
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
                                             final BasicFileAttributes attributes) {
                if (exclude.stream().noneMatch(m -> m.matches(file))) {
                    if (include.test(file)) {
                        files.add(J2clPath.with(file));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }
}
