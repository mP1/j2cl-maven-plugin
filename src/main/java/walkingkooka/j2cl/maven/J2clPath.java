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

import com.google.j2cl.common.SourceUtils.FileInfo;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.collect.set.SortedSets;
import walkingkooka.file.Files2;
import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.reflect.PackageName;
import walkingkooka.text.CaseSensitivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A more object oriented path abstraction with numerous methods to do useful stuff.
 */
public final class J2clPath implements Comparable<J2clPath> {

    /**
     * A common prefix for all file and directories related to this plugin.
     */
    public static final String PREFIX = "walkingkooka-j2cl-maven-plugin";

    /**
     * The file prefix for the various user files that may appear within source or archive describing various plugin features.
     */
    public static final String FILE_PREFIX = "." + PREFIX;

    /**
     * Matches all files but not directories.
     */
    public static final Predicate<Path> ALL_FILES = Files::isRegularFile;

    /**
     * Matches paths with a class file extension.
     */
    public static final Predicate<Path> CLASS_FILEEXTENSION = fileEndsWith(".class");

    /**
     * Matches existing class files.
     */
    public static final Predicate<Path> CLASS_FILES = ALL_FILES.and(CLASS_FILEEXTENSION);

    /**
     * Matches paths with a java file extension.
     */
    public static final Predicate<Path> JAVA_FILEEXTENSION = fileEndsWith(".java");

    /**
     * Matches existing java files.
     */
    public static final Predicate<Path> JAVA_FILES = ALL_FILES.and(JAVA_FILEEXTENSION);

    /**
     * Matches existing js files.
     */
    public static final Predicate<Path> JAVASCRIPT_FILES = ALL_FILES.and(fileEndsWith(".js"));

    /**
     * Matches existing native.js files.
     */
    public static final Predicate<Path> NATIVE_JAVASCRIPT_FILES = ALL_FILES.and(fileEndsWith(".native.js"));

    /**
     * Matches all files that end with the given extension, assumes the extension includes a leading dot.
     */
    private static Predicate<Path> fileEndsWith(final String extension) {
        return (p) -> p.getFileName().toString().endsWith(extension);
    }

    /**
     * This filter filters the /META-INF directory when extracting from an archive.
     */
    public static final Predicate<Path> WITHOUT_META_INF = (p) -> false == p.startsWith("/META-INF");

    /**
     * Matches all files except for java source.
     */
    public static final Predicate<Path> ALL_FILES_EXCEPT_JAVA = ALL_FILES.and((p) -> !JAVA_FILEEXTENSION.test(p));

    public static List<File> toFiles(final Collection<J2clPath> paths) {
        return paths.stream()
                .map(J2clPath::file)
                .collect(Collectors.toList());
    }

    public static J2clPath with(final Path path) {
        return new J2clPath(path);
    }

    private J2clPath(final Path path) {
        super();
        this.path = path;
    }

    @SuppressWarnings("ThrowableNotThrown")
    public J2clPath absentOrFail() throws IOException {
        if (this.exists().isPresent()) {
            throw new IllegalArgumentException("Directory exists: " + this);
        }

        return this.createIfNecessary();
    }

    public J2clPath append(final String directory) {
        return J2clPath.with(
                Paths.get(
                        this.path.toString(),
                        directory
                )
        );
    }

    /**
     * An identity {@link BiFunction} that does not modify the given file content.
     * Most copy operations except for shading dont want to modify each file they encounter.
     */
    public final static BiFunction<byte[], J2clPath, byte[]> COPY_FILE_CONTENT_VERBATIM = (b, path) -> b;

    /**
     * Copies the files from the given source to this directory. Existing files are replaced, new files are copied.
     */
    public Collection<J2clPath> copyFiles(final J2clPath src,
                                          final Collection<J2clPath> files,
                                          final BiFunction<byte[], J2clPath, byte[]> contentTransformer) throws IOException {
        final Path srcPath = src.path();
        final Path destPath = this.path();

        final List<J2clPath> copied = Lists.array();

        for (final J2clPath file : files) {
            final Path filePath = file.path();
            final String relative = srcPath.relativize(filePath).toString();
            final Path copyTarget = destPath.resolve(relative);

            Files.createDirectories(copyTarget.getParent());

            final J2clPath copyTargetPath = J2clPath.with(copyTarget);
            Files.write(
                    copyTarget,
                    contentTransformer.apply(Files.readAllBytes(filePath), copyTargetPath)
            );

            copied.add(copyTargetPath);
        }

        return copied;
    }

    public J2clPath createIfNecessary() throws IOException {
        Files.createDirectories(this.path());
        return this;
    }

    public Optional<J2clPath> exists() {
        return Optional.ofNullable(Files.exists(this.path()) ? this : null);
    }

    /**
     * This may be used to test if a path is a source root for a generated sources directory like generated-sources.
     */
    public boolean isGeneratedDirectory() {
        final File file = this.file();
        return file.isDirectory() && file.getName().startsWith("generated");
    }

    // extract..........................................................................................................

    /**
     * Extract ALL the files from this archive, returning number of files extracted
     */
    public Set<J2clPath> extractArchiveFiles(final Predicate<Path> filter,
                                             final J2clPath target,
                                             final TreeLogger logger) throws IOException {
        final URI uri = URI.create("jar:" + this.path().toAbsolutePath().toUri());
        try (final FileSystem zip = FileSystems.newFileSystem(uri, Maps.empty())) {
            return this.extractArchiveFiles0(zip.getPath("/"),
                    filter,
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
                                               final Predicate<Path> filter,
                                               final J2clPath target,
                                               final TreeLogger logger) throws IOException {
        final Set<J2clPath> files = J2clPath.with(source).gatherFiles(filter.and(J2clPath.ALL_FILES));
        if (files.isEmpty()) {
            logger.indentedLine("No files");
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
                                       final TreeLogger logger) throws IOException {
        for (final J2clPath file : files) {
            final Path filePath = file.path();
            final Path pathInZip = root.relativize(filePath);
            final Path copyTarget = Paths.get(target.toString()).resolve(pathInZip.toString());
            if (Files.exists(copyTarget)) {
                continue;
            }

            Files.createDirectories(copyTarget.getParent());
            Files.copy(filePath, copyTarget, StandardCopyOption.REPLACE_EXISTING);
        }

        logger.paths("", files, TreeFormat.TREE);
    }

    /**
     * Uses to collect all files that match the {@link BiPredicate} and returns a sorted {@link Set}.
     */
    public Set<J2clPath> gatherFiles(final Predicate<Path> filter) throws IOException {
        return Files.find(this.path(), Integer.MAX_VALUE, (p, a) -> filter.test(p))
                .map(J2clPath::with)
                .sorted()
                .collect(Collectors.toCollection(SortedSets::tree));
    }

    public boolean isFile() {
        return Files.isRegularFile(this.path());
    }

    /**
     * Builds a new path holding the ignore file. Note that ignored files are simply ignored with no logging happening.
     */
    public Optional<PathMatcher> ignoredFiles() throws IOException {
        return this.append(J2clArtifact.IGNORED_FILES)
                .pathMatcher(
                        this
                );
    }

    /**
     * Tries to read this file turning each non comment / non empty line into a {@link PathMatcher}.
     */
    private Optional<PathMatcher> pathMatcher(final J2clPath parent) throws IOException {
        PathMatcher pathMatcher = null;

        if (this.exists().isPresent()) {
            final Path path = this.path;

            pathMatcher = Files2.relativePathMatcher(
                    Files2.globPatterns(
                            new String(
                                    Files.readAllBytes(path),
                                    Charset.defaultCharset()
                            ),
                            CaseSensitivity.SENSITIVE
                    ),
                    parent.path()
            );
        }

        return Optional.ofNullable(pathMatcher);
    }

    /**
     * Returns true if this file is a java file.
     */
    public boolean isJava() {
        return this.filename().endsWith(".java");
    }

    /**
     * The file that will capture the components of a hashing.
     */
    J2clPath hashFile() {
        return this.append(HASH_FILE);
    }

    private final static String HASH_FILE = "hash.txt";

    /**
     * Builds a new path holding the output directory within this directory.
     */
    public J2clPath output() {
        return this.append(OUTPUT);
    }

    /**
     * Only returns true if this path is the output directory of an UNPACK.
     */
    public boolean isUnpackOutput(final J2clArtifact artifact,
                                  final J2clMavenContext context) {
        return artifact.isDependency() &&
                this.filename().equals(OUTPUT) &&
                this.path()
                        .getParent()
                        .getFileName()
                        .toString()
                        .equals(
                                context.directoryName(artifact, J2clTaskKind.UNPACK)
                        );
    }

    private final static String OUTPUT = "output";

    /**
     * Returns a {@link PathMatcher} for all the patterns within any present public files.
     */
    public Optional<PathMatcher> publicFiles() throws IOException {
        return this.append(J2clArtifact.PUBLIC_FILES)
                .pathMatcher(
                        this
                );
    }

    public J2clPath parent() {
        return new J2clPath(this.path().getParent());
    }

    public Path path() {
        return this.path;
    }

    private final Path path;

    public String filename() {
        return this.path().getFileName().toString();
    }

    public File file() {
        return this.path().toFile();
    }

    /**
     * Performs a recursive file delete on this path.
     */
    public J2clPath removeAll() throws IOException {
        Files.walkFileTree(
                this.path(),
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir,
                                                             final BasicFileAttributes attrs) throws IOException {
                        this.directoryDepth++;
                        return super.preVisitDirectory(dir, attrs);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir,
                                                              final IOException cause) throws IOException {
                        if (this.directoryDepth-- > 1) {
                            Files.delete(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    private int directoryDepth = 0;

                    @Override
                    public FileVisitResult visitFile(final Path file,
                                                     final BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                }
        );
        return this;
    }

    /**
     * Builds a new path holding the shade mapping file.
     */
    J2clPath shadeFile() {
        return this.append(J2clArtifact.SHADE_FILE);
    }

    /**
     * Used to read the {@link J2clArtifact#SHADE_FILE} properties file. Note the {@link Map} keeps entries in the file order.
     */
    Map<PackageName, PackageName> readShadeFile() throws IOException {
        return J2clArtifactShadeFile.readShadeFile(
                new FileInputStream(this.file())
        );
    }

    /**
     * Returns true if the given {@link Path} belongs to the super under this path.
     */
    public boolean isSuperSource(final Path path) {
        return this.path.relativize(path).toString().startsWith("super");
    }

    public boolean isTestAnnotation() {
        final Path path = this.path();
        return Files.isDirectory(path) && path.getFileName().toString().equals("test-annotations");
    }

    /**
     * Builds the name of the testsuite javascript file from the class name. It should appear in the output directory.
     */
    public J2clPath testAdapterSuiteGeneratedFilename(final String testClassName) {
        //  'org.gwtproject.timer.client.TimerJ2clTest');
        return this.append(testClassName.replace('.', File.separatorChar) + TESTSUITE_FILEEXTENSION);
    }

    /**
     * Using the output directory and the testClassName and computes the testSuite js path.
     */
    public J2clPath testAdapterSuiteCorrectFilename(final String testClassName) {
        // goog.module('javatests.org.gwtproject.timer.client.TimerJ2clTest_AdapterSuite');
        return this.append(("/javatests/" + testClassName + "_AdapterSuite")
                .replace('.', File.separatorChar)
                + ".js");
    }

    private final static String TESTSUITE_FILEEXTENSION = ".testsuite";

    public FileInfo toFileInfo(final J2clPath base) {
        final Path path = this.path();
        return FileInfo.create(
                path.toString(),
                base.path().relativize(path).toString()
        );
    }

    /**
     * Writes the content to this path.
     */
    public J2clPath writeFile(final byte[] contents) throws IOException {
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
