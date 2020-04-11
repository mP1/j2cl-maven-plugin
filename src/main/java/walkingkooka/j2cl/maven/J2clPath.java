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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * A more object oriented path abstraction with numerous methods to do useful stuff.
 */
final class J2clPath implements Comparable<J2clPath> {

    static final String FILE_PREFIX = ".walkingkooka-j2cl-maven-plugin";

    static final BiPredicate<Path, BasicFileAttributes> CLASS_FILES = fileEndsWith(".class");
    static final BiPredicate<Path, BasicFileAttributes> JAVA_FILES = fileEndsWith(".java");
    static final BiPredicate<Path, BasicFileAttributes> JAVASCRIPT_FILES = fileEndsWith(".js");
    static final BiPredicate<Path, BasicFileAttributes> NATIVE_JAVASCRIPT_FILES = fileEndsWith(".native.js");

    /**
     * Matches all files that end with the given extension, assumes the extension includes a leading dot.
     */
    private static BiPredicate<Path, BasicFileAttributes> fileEndsWith(final String extension) {
        return (p, a) -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(extension);
    }

    /**
     * Matches all files but not directories.
     */
    static final BiPredicate<Path, BasicFileAttributes> ALL_FILES = (p, a) -> Files.isRegularFile(p);

    /**
     * Matches all files except for java source.
     */
    static final BiPredicate<Path, BasicFileAttributes> ALL_FILES_EXCEPT_JAVA = (p, a) -> {
        return Files.isRegularFile(p) && false == p.getFileName().toString().endsWith(".java");
    };

    static List<File> toFiles(final Collection<J2clPath> paths) {
        return paths.stream().map(J2clPath::file).collect(Collectors.toList());
    }

    static List<FileInfo> toFileInfo(final Collection<J2clPath> files,
                                     final J2clPath base) {
        return files.stream()
                .map(p -> {
                    final Path path = p.path();
                    return FileInfo.create(path.toString(), base.path().relativize(path).toString());
                })
                .collect(Collectors.toList());
    }

    static J2clPath with(final Path path) {
        return new J2clPath(path);
    }

    private J2clPath(final Path path) {
        super();
        this.path = path;
    }

    @SuppressWarnings("ThrowableNotThrown")
    J2clPath absentOrFail() throws IOException {
        if (this.exists().isPresent()) {
            throw new IllegalArgumentException("Directory exists: " + this);
        }

        return this.createIfNecessary();
    }

    J2clPath append(final String directory) {
        return J2clPath.with(Paths.get(this.path.toString(), directory));
    }

    /**
     * Copies the files from the given source to this directory.
     */
    Collection<J2clPath> copyFiles(final J2clPath src,
                                   final Collection<J2clPath> files,
                                   final J2clLinePrinter logger) throws IOException {
        return this.copyFiles(src,
                files,
                J2clPath::identityBiFunction,
                logger);
    }

    /**
     * Returns the content unmodified. This is the default behaviour of all copy operations except for {@link J2clStep#SHADE_JAVA_SOURCE}
     */
    private static byte[] identityBiFunction(final byte[] content, final J2clPath path) {
        return content;
    }

    /**
     * Copies the files from the given source to this directory.
     */
    Collection<J2clPath> copyFiles(final J2clPath src,
                                   final Collection<J2clPath> files,
                                   final BiFunction<byte[], J2clPath, byte[]> contentTransformer,
                                   final J2clLinePrinter logger) throws IOException {
        final Path srcPath = src.path();
        final Path destPath = this.path();

        final List<J2clPath> copied = Lists.array();

        for (final J2clPath file : files) {
            final Path filePath = file.path();
            final String relative = srcPath.relativize(filePath).toString();
            final Path copyTarget = destPath.resolve(relative);
            if (Files.exists(copyTarget)) {
                continue;
            }

            Files.createDirectories(copyTarget.getParent());

            final J2clPath copyTargetPath = J2clPath.with(copyTarget);
            Files.write(copyTarget,
                    contentTransformer.apply(Files.readAllBytes(filePath), copyTargetPath));

            copied.add(copyTargetPath);
        }

        if (copied.isEmpty()) {
            logger.printLine("Copied");
            logger.indent();
            {
                logger.printIndentedLine(src.toString());
                logger.printLine("0 file(s)");
            }
            logger.outdent();
        } else {
            logger.printIndented("Copied", copied);
        }
        return copied;
    }

    J2clPath createIfNecessary() throws IOException {
        Files.createDirectories(this.path());
        return this;
    }

    Optional<J2clPath> exists() {
        return Optional.ofNullable(Files.exists(this.path()) ? this : null);
    }

    // extract..........................................................................................................

    /**
     * Extract ALL the files from this archive, returning number of files extracted
     */
    Set<J2clPath> extractArchiveFiles(final J2clPath target,
                                      final J2clLinePrinter logger) throws IOException {
        final URI uri = URI.create("jar:" + this.path().toAbsolutePath().toUri());
        try (final FileSystem zip = FileSystems.newFileSystem(uri, Maps.empty())) {
            return this.extractArchiveFiles0(zip.getPath("/"),
                    target,
                    logger);
        } catch (final FileSystemAlreadyExistsException cause) {
            throw new IOException("File " + uri + " exists", cause);
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
        for (final J2clPath file : files) {
            final Path filePath = file.path();
            final Path pathInZip = root.relativize(filePath);
            final Path copyTarget = Paths.get(target.toString()).resolve(pathInZip.toString());
            if (Files.exists(copyTarget)) {
                continue;
            }

            Files.createDirectories(copyTarget.getParent());
            Files.copy(filePath, copyTarget);
        }

        logger.printIndented("Extracting", files);
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

    boolean isFile() {
        return Files.isRegularFile(this.path());
    }

    /**
     * Builds a new path holding the ignore file.
     */
    J2clPath ignoredFiles() {
        return this.append(IGNORED_FILES);
    }

    /**
     * The name of the ignore file which is used during the unpack phase to filter files.
     */
    private static final String IGNORED_FILES = FILE_PREFIX + "-ignored-files.txt";

    /**
     * Returns true if this file is a java file.
     */
    boolean isJava() {
        return this.filename().endsWith(".java");
    }

    /**
     * Builds a new path holding the output directory within this directory.
     */
    J2clPath output() {
        return this.append(OUTPUT);
    }

    /**
     * Only returns true if this path is the output directory of an UNPACK.
     */
    boolean isUnpackOutput() {
        return this.filename().equals(OUTPUT) &&
                this.path().getParent().getFileName().toString().equals(J2clStep.UNPACK.directoryName());
    }

    private final static String OUTPUT = "output";

    J2clPath parent() {
        return new J2clPath(this.path().getParent());
    }

    Path path() {
        return this.path;
    }

    private final Path path;

    String filename() {
        return this.path().getFileName().toString();
    }

    File file() {
        return this.path().toFile();
    }

    /**
     * Performs a recursive file delete on this path.
     */
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

    /**
     * Builds a new path holding the shade mapping file.
     */
    J2clPath shadeFile() {
        return this.append(SHADE_FILE);
    }

    /**
     * The name of the shade file used during {@link J2clStep#SHADE_JAVA_SOURCE} and the package prefix to be removed.
     */
    static final String SHADE_FILE = FILE_PREFIX + "-shade.txt";

    /**
     * Used to read the {@link #SHADE_FILE} properties file.
     */
    Map<String, String> readShadeFile() throws IOException {
        try (final InputStream file = new FileInputStream(this.file())) {
            final Properties properties = new Properties();
            properties.load(file);

            final Map<String, String> map = Maps.sorted();
            properties.forEach((k, v) -> map.put((String) k, (String) v));
            return map;
        }
    }

    /**
     * Returns true if the given {@link Path} belongs to the super under this path.
     */
    boolean isSuperSource(final Path path) {
        return this.path.relativize(path).toString().startsWith("super");
    }

    boolean isTestAnnotation() {
        final Path path = this.path();
        return Files.isDirectory(path) && path.getFileName().toString().equals("test-annotations");
    }

    /**
     * Builds the name of the testsuite javascript file from the class name. It should appear in the output directory.
     */
    J2clPath testAdapterSuiteGeneratedFilename(final String testClassName) {
        //  'org.gwtproject.timer.client.TimerJ2clTest');
        return this.append(testClassName.replace('.', File.separatorChar) + TESTSUITE_FILEEXTENSION);
    }

    /**
     * Using the output directory and the testClassName and computes the testSuite js path.
     */
    J2clPath testAdapterSuiteCorrectFilename(final String testClassName) {
        // goog.module('javatests.org.gwtproject.timer.client.TimerJ2clTest_AdapterSuite');
        return this.append(("/javatests/" + testClassName + "_AdapterSuite")
                .replace('.', File.separatorChar)
                + ".js");
    }

    private final static String TESTSUITE_FILEEXTENSION = ".testsuite";

    /**
     * Writes the content to this path.
     */
    J2clPath writeFile(final byte[] contents) throws IOException {
        Files.write(this.path(), contents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return this;
    }

    // Object...........................................................................................................

    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof J2clPath && this.equals0((J2clPath) other);
    }

    private boolean equals0(final J2clPath other) {
        return this.path.equals(other.path);
    }

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
