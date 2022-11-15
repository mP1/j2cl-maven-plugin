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

import com.google.common.collect.Lists;
import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Takes a {@link J2clDependency} and computes the hash for the files directly belonging to the artifact and then its dependencies.
 */
final class J2clStepWorkerHash extends J2clStepWorker {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerHash();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerHash() {
        super();
    }

    @Override
    J2clStepResult execute(final J2clDependency artifact,
                           final J2clStep step,
                           final TreeLogger logger) throws Exception {
        final Set<String> hashItemNames = Sets.sorted();
        final HashBuilder hash = artifact.context()
                .computeHash(hashItemNames);

        this.hashDependencies(artifact, hash, hashItemNames, logger);
        this.hashArtifactSources(artifact, hash, hashItemNames, logger);

        final J2clStepDirectory directory = artifact.setDirectory(hash.toString())
                .step(J2clStep.HASH);

        final String txt = hashItemNames.stream()
                .map(t -> {
                    if (t.startsWith(DEPENDENCIES)) {
                        // remove any run on leading zeroes...added by hashDependencies
                        final int colon = t.indexOf(':');
                        t = DEPENDENCIES + Integer.parseInt(t.substring(DEPENDENCIES.length(), colon)) + t.substring(colon);
                    }
                    return t;
                })
                .collect(Collectors.joining("\n"));

        directory.hashFile()
                .writeFile(txt.getBytes(Charset.defaultCharset()));
        return J2clStepResult.SUCCESS;
    }

    private void hashDependencies(final J2clDependency artifact,
                                  final HashBuilder hash,
                                  final Set<String> hashItemNames,
                                  final TreeLogger logger) throws IOException {
        final Set<J2clDependency> dependencies = artifact.dependencies(); // dependencies();
        logger.line(dependencies.size() + " Dependencies");
        logger.indent();

        int i = 0;
        for (final J2clDependency dependency : dependencies) {
            logger.line(dependency.toString());
            logger.indent();
            {
                // leading zeroes added to keep keys in numeric order, so dependencies-0 is followed by dependencies-1 not dependencies-10
                final J2clPath dependencyFile = dependency.artifactFileOrFail();
                hashItemNames.add(DEPENDENCIES + CharSequences.padLeft("" + i, 10, '0') + ": " + dependency.coords());
                hash.append(dependencyFile.path());

                i++;
            }
            logger.outdent();
        }

        logger.endOfList();
        logger.outdent();
        logger.emptyLine();
    }

    private final static String DEPENDENCIES = "dependencies-";

    private void hashArtifactSources(final J2clDependency artifact,
                                     final HashBuilder hash,
                                     final Set<String> hashItemNames,
                                     final TreeLogger logger) throws IOException {
        final List<J2clPath> compileSourcesRoot = artifact.sourcesRoot();

        if (compileSourcesRoot.isEmpty()) {
            this.hashArchiveFile(artifact, hash, hashItemNames, logger);
        } else {
            this.hashCompileSourceRoots(compileSourcesRoot.stream().map(J2clPath::path).collect(Collectors.toList()),
                    hash,
                    hashItemNames,
                    logger);
        }
    }

    private void hashArchiveFile(final J2clDependency artifact,
                                 final HashBuilder hash,
                                 final Set<String> hashItemNames,
                                 final TreeLogger logger) throws IOException {
        final J2clPath file = artifact.artifactFileOrFail();
        try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.file().toURI()), Collections.emptyMap())) {
            this.hashCompileSourceRoots(zip.getRootDirectories(),
                    hash,
                    hashItemNames,
                    logger);
        }
    }


    private void hashCompileSourceRoots(final Iterable<Path> roots,
                                        final HashBuilder hash,
                                        final Set<String> hashItemNames,
                                        final TreeLogger logger) throws IOException {
        logger.line(Lists.newArrayList(roots).size() + " Source root(s)");
        logger.indent();

        for (final Path root : roots) {
            hashItemNames.add("compile-source-root: " + root.getFileName());
            this.hashDirectoryTree(root, hash, logger);
        }

        logger.endOfList();
        logger.outdent();
    }

    private void hashDirectoryTree(final Path root,
                                   final HashBuilder hash,
                                   final TreeLogger logger) throws IOException {
        logger.line(root.toString());
        logger.indent();

        Files.walkFileTree(root, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path path,
                                                     final BasicFileAttributes basicFileAttributes) {
                logger.line(path.getFileName() + " (dir)");
                logger.indent();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path path,
                                             final BasicFileAttributes basicFileAttributes) throws IOException {
                hash.append(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path path,
                                                   final IOException cause) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path path,
                                                      final IOException cause) {
                logger.outdent();
                return FileVisitResult.CONTINUE;
            }
        });

        logger.outdent();
    }
}
