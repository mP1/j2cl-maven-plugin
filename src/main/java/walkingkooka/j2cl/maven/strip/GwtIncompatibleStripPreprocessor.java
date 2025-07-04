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

package walkingkooka.j2cl.maven.strip;

import com.google.j2cl.common.OutputUtils;
import com.google.j2cl.common.Problems;
import com.google.j2cl.common.SourceUtils.FileInfo;
import com.google.j2cl.tools.gwtincompatible.GwtIncompatibleStripper;
import javaemul.internal.annotations.GwtIncompatible;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.set.Sets;
import walkingkooka.collect.set.SortedSets;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    static J2clTaskResult execute(final List<J2clPath> sourceRoots,
                                  final J2clPath output,
                                  final TreeLogger logger) throws IOException {
        output.exists()
                .orElseThrow(() -> new IllegalArgumentException("Output not a directory or does not exist: " + CharSequences.quote(output.toString())));

        J2clTaskResult result;

        // FileInfo must have the source path otherwise stripper will write files to the wrong place.
        final List<FileInfo> javaFiles = prepareJavaFiles(sourceRoots, output, logger);

        final int javaFileCount = javaFiles.size();

        if (javaFileCount > 0) {
            result = processStripAnnotationsFiles(javaFiles, output, logger);

            copyJavascriptFiles(sourceRoots, output, logger);
            logger.paths(
                    "Output file(s)",
                    output.gatherFiles(J2clPath.ALL_FILES),
                    TreeFormat.TREE
            );

        } else {
            logger.indentedLine("No files found");

            result = J2clTaskResult.ABORTED;
        }

        return result;
    }

    private static List<FileInfo> prepareJavaFiles(final List<J2clPath> sourceRoots,
                                                   final J2clPath output,
                                                   final TreeLogger logger) throws IOException {
        final List<FileInfo> javaFiles = Lists.array();

        final boolean debugEnabled = logger.isDebugEnabled();

        if (debugEnabled) {
            logger.line("Preparing java files");
            logger.indent();
        }
        {

            for (final J2clPath sourceRoot : sourceRoots) {
                {
                    final Set<J2clPath> fromFiles = gatherFiles(
                            sourceRoot,
                            J2clPath.JAVA_FILES
                    );

                    // find then copy from unpack to $output
                    final Collection<J2clPath> files = output.copyFiles(
                            sourceRoot,
                            fromFiles,
                            J2clPath.COPY_FILE_CONTENT_VERBATIM
                    );

                    logger.paths(
                            "",
                            fromFiles,
                            TreeFormat.TREE
                    );

                    // necessary to prepare FileInfo with correct sourceRoot otherwise stripped files will be written back to the wrong place.
                    javaFiles.addAll(
                            toFileInfos(
                                    files,
                                    output
                            )
                    );
                }
            }
        }
        if (debugEnabled) {
            logger.outdent();
        }

        return javaFiles;
    }

    private static List<FileInfo> toFileInfos(final Collection<J2clPath> files,
                                              final J2clPath base) {
        return files.stream()
                .map(p -> p.toFileInfo(base))
                .collect(Collectors.toList());
    }

    /**
     * Invokes the java preprocesor which use annotations to discover classes, methods and fields to remove from the actual source files.
     * Because the source files are modified a previous task will have taken copied and place them in this output ready for modification if necessary.
     * Errors will also be logged.
     */
    private static J2clTaskResult processStripAnnotationsFiles(final List<FileInfo> javaFilesInput,
                                                               final J2clPath output,
                                                               final TreeLogger logger) {
        J2clTaskResult result;

        logger.line("GwtIncompatibleStripper");
        {
            logger.indent();
            {
                logger.fileInfos("Source(s)", javaFilesInput, TreeFormat.TREE);
                logger.path("Output", output);

                final Problems problems = new Problems();

                try (final OutputUtils.Output outputOutput = OutputUtils.initOutput(
                        output.path(),
                        problems
                )) {
                    GwtIncompatibleStripper.preprocessFiles(
                            javaFilesInput,
                            outputOutput,
                            problems,
                            GwtIncompatible.class.getSimpleName()
                    );
                }

                logger.strings("Message(s)", problems.getMessages());

                final List<String> errors = problems.getErrors();
                logger.strings("Error(s)", errors);
                logger.strings("Warning(s)", problems.getWarnings());

                result = errors.isEmpty() ?
                        J2clTaskResult.SUCCESS :
                        J2clTaskResult.FAILED;
            }
            logger.outdent();
        }

        return result;
    }

    private static void copyJavascriptFiles(final List<J2clPath> sourceRoots,
                                            final J2clPath output,
                                            final TreeLogger logger) throws IOException {
        logger.line("Copy *.js from source root(s) to output");

        for (final J2clPath sourceRoot : sourceRoots) {
            final Set<J2clPath> copy = gatherFiles(
                    sourceRoot,
                    J2clPath.JAVASCRIPT_FILES
            );

            output.copyFiles(
                    sourceRoot,
                    copy,
                    J2clPath.COPY_FILE_CONTENT_VERBATIM
            );

            logger.paths(
                    "",
                    copy,
                    TreeFormat.TREE
            );
        }
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
        final SortedSet<J2clPath> files = SortedSets.tree();

        Files.walkFileTree(
                root.path(),
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir,
                                                             final BasicFileAttributes attrs) throws IOException {
                        if (0 == this.depth++) {
                            final Optional<PathMatcher> maybeIgnoreFile = J2clPath.with(dir)
                                    .ignoredFiles();
                            if (maybeIgnoreFile.isPresent()) {
                                this.ignore = maybeIgnoreFile.get();
                            }
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    private int depth = 0;

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir,
                                                              final IOException cause) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(final Path file,
                                                     final BasicFileAttributes attributes) {
                        final PathMatcher ignore = this.ignore;
                        if (null == ignore || false == ignore.matches(file)) {
                            if (include.test(file)) {
                                files.add(J2clPath.with(file));
                            }
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    private PathMatcher ignore = null;
                }
        );

        return files;
    }
}
