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
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A more object oriented path abstraction with numerous methods to do useful stuff.
 */
final class J2clPath implements Comparable<J2clPath> {

    static final BiPredicate<Path, BasicFileAttributes> JAVA_FILES = (p, a) -> CharSequences.endsWith(p.toString(), ".java");
    static final BiPredicate<Path, BasicFileAttributes> JAVASCRIPT_FILES = (p, a) -> CharSequences.endsWith(p.toString(), ".js");
    static final BiPredicate<Path, BasicFileAttributes> NATIVE_JAVASCRIPT_FILES = (p, a) -> CharSequences.endsWith(p.toString(), ".native.js");
    static final BiPredicate<Path, BasicFileAttributes> ALL_FILES = (p, a) -> true;

    static List<File> toFiles(final Collection<J2clPath> paths) {
        return paths.stream().map(J2clPath::file).collect(Collectors.toList());
    }

    static List<FileInfo> toFileInfo(final List<J2clPath> files,
                                     final J2clPath base) {
        return files.stream()
                .map(p -> {
                    final Path path = p.path();
                    return FileInfo.create(path.toString(), base.path().relativize(path).toString());
                })
                .collect(Collectors.toList());
    }

    static Path[] toPaths(final List<J2clPath> files) {
        return files.stream()
                .map(J2clPath::path)
                .toArray(Path[]::new);
    }

    static J2clPath with(final Path path) {
        return new J2clPath(path);
    }

    private J2clPath(final Path path) {
        super();
        this.path = path;
    }

    boolean isFile() {
        return Files.isRegularFile(this.path());
    }

    J2clPath parent() {
        return new J2clPath(this.path().getParent());
    }

    J2clPath createIfNecessary() throws IOException {
        Files.createDirectories(this.path());
        return this;
    }

    @SuppressWarnings("ThrowableNotThrown")
    J2clPath emptyOrFail() throws IOException {
        this.exists()
                .ifPresent((p) -> new IllegalArgumentException("Directory exists: " + this));

        return this.createIfNecessary();
    }

    Optional<J2clPath> exists() {
        return Optional.ofNullable(Files.exists(this.path()) ? this : null);
    }

    J2clPath removeAll() throws IOException {
        Files.walkFileTree(this.path(),
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir,
                                                              final IOException cause) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(final Path file,
                                                     final BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
        return this;
    }

    public J2clPath append(final String directory) {
        return J2clPath.with(Paths.get(this.path.toString(), directory));
    }

    /**
     * Uses to collect all files that match the {@link BiPredicate} and returns a sorted {@link Set}.
     */
    Set<J2clPath> gatherFiles(final BiPredicate<Path, BasicFileAttributes> matcher) throws IOException {
        return Files.find(this.path(), Integer.MAX_VALUE, matcher)
                .map(J2clPath::with)
                .sorted()
                .collect(Collectors.toCollection(Sets::sorted));
    }

    /**
     * Copies the files from the given source to this directory.
     */
    Collection<J2clPath> copyFiles(final J2clPath src,
                                   final Collection<J2clPath> files,
                                   final Consumer<String> logger) throws IOException {
        final Path srcPath = src.path();
        final Path destPath = this.path();

        final List<J2clPath> copied = Lists.array();

        for (final J2clPath file : files) {
            final Path filePath = file.path();
            Path copyTarget = destPath.resolve(srcPath.relativize(filePath).toString());
            if (Files.exists(copyTarget)) {
                continue;
            }

            logger.accept(srcPath.relativize(filePath).toString());

            Files.createDirectories(copyTarget.getParent());
            Files.copy(filePath, copyTarget);

            copied.add(J2clPath.with(copyTarget));
        }

        return copied;
    }

    /**
     * Extract ALL the files from this archive, returning number of files extracted
     */
    Set<J2clPath> extractArchiveFiles(final J2clPath target,
                                      final J2clLinePrinter logger) throws IOException {
        try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + this.path().toAbsolutePath().toUri()), Maps.empty())) {
            return this.extractArchiveFiles0(zip.getPath("/"),
                    target,
                    logger);
        }
    }

    /**
     * First gets an alphabetical listing of all files in the given source and then proceeds to copy them to the destination.
     * This produces output that shows the files processed in alphabetical order.
     */
    private Set<J2clPath> extractArchiveFiles0(final Path source,
                                               final J2clPath target,
                                               final J2clLinePrinter logger) throws IOException {
        final Set<J2clPath> files = J2clPath.with(source).gatherFiles(J2clPath.ALL_FILES);
        if (files.isEmpty()) {
            logger.printIndentedLine("No files");
        } else {
            this.extractArchivesFiles1(source, target, files, logger);
        }

        return Sets.readOnly(files);
    }

    /**
     * Extracts the given files, note existing files will not be overwritten.
     */
    private void extractArchivesFiles1(final Path root,
                                       final J2clPath target,
                                       final Set<J2clPath> files,
                                       final J2clLinePrinter logger) throws IOException {
        logger.indent();
        {

            for (final J2clPath file : files) {
                final Path filePath = file.path();
                final Path pathInZip = root.relativize(filePath);
                final Path copyTarget = Paths.get(target.toString()).resolve(pathInZip.toString());
                if (Files.exists(copyTarget)) {
                    continue;
                }

                Files.createDirectories(copyTarget.getParent());
                Files.copy(filePath, copyTarget);

                logger.printLine(file.toString());
            }
        }
        logger.outdent();
    }

    String filename() {
        return this.path().getFileName().toString();
    }

    Path path() {
        return this.path;
    }

    File file() {
        return this.path().toFile();
    }

    private final Path path;

    // Object...........................................................................................................

    @Override
    public String toString() {
        return this.path.toString();
    }

    // Comparable.......................................................................................................

    @Override
    public int compareTo(final J2clPath other) {
        return this.toString().compareTo(other.toString());
    }
}
