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
import walkingkooka.collect.set.Sets;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A more object oriented path abstraction with numerous methods to do useful stuff.
 */
final class J2clPath implements Comparable<J2clPath> {

    static final PathMatcher JAVA_FILES = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
    static final PathMatcher JAVASCRIPT_FILES = FileSystems.getDefault().getPathMatcher("glob:**/*.js");
    static final PathMatcher NATIVE_JAVASCRIPT_FILES = FileSystems.getDefault().getPathMatcher("glob:**/*.native.js");

    static List<File> toFiles(final Collection<J2clPath> paths) {
        return paths.stream().map(J2clPath::file).collect(Collectors.toList());
    }

    static List<FileInfo> toFileInfo(final List<J2clPath> files) {
        return files.stream()
                .map(J2clPath::fileInfo)
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

    boolean isJavaFile() {
        return this.path().toString().endsWith(".java");
    }

    boolean isFile() {
        return Files.isRegularFile(this.path());
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
     * Used to collect all the files and then sort them alphabetically under the given root.
     * This allows directories and archive to be processed in alphabetical order producing a consistent and readable output.
     */
    final Set<J2clPath> gatherFiles() throws IOException {
        final Set<J2clPath> files = Sets.sorted();

        Files.walkFileTree(this.path(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes attrs) {
                files.add(J2clPath.with(file));
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Copies the files from the given source to this directory.
     */
    List<J2clPath> copyFiles(final J2clPath src,
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
        logger.accept(files.size() + " file(s) copied");

        return copied;
    }

    /**
     * Finds any files under the roots using the matches, returning {@link File files} with absolute paths.
     */
    static List<J2clPath> findFiles(final List<J2clPath> roots,
                                    final PathMatcher... matchers) throws IOException {
        final List<J2clPath> files = Lists.array();
        for (final J2clPath root : roots) {
            files.addAll(root.findFiles(matchers));
        }

        return files;
    }

    /**
     * Finds any files under the roots using the matches, returning {@link File files} with absolute paths.
     * The files are also sorted alphabetically to aide readability when pretty printed by the logger.
     */
    List<J2clPath> findFiles(final PathMatcher... matchers) throws IOException {
        return this.exists().isPresent() ?
                this.findFiles0(matchers) :
                Lists.empty();
    }

    private List<J2clPath> findFiles0(final PathMatcher... matchers) throws IOException {
        return Files.find(this.path(), Integer.MAX_VALUE, ((path, basicFileAttributes) -> Arrays.stream(matchers).anyMatch(m -> m.matches(path))))
                .map(J2clPath::with)
                .sorted()
                .collect(Collectors.toList());
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

    private FileInfo fileInfo() {
        final String path = this.path().toString();
        return FileInfo.create(path, path);
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
