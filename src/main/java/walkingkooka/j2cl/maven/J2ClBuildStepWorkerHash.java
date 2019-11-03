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
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.net.URI;
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
final class J2ClBuildStepWorkerHash extends J2clBuildStepWorker {

    /**
     * Singleton
     */
    static J2clBuildStepWorker instance() {
        return new J2ClBuildStepWorkerHash();
    }

    /**
     * Use singleton
     */
    private J2ClBuildStepWorkerHash() {
        super();
    }

    @Override
    J2clBuildStepResult execute(final J2clDependency artifact,
                                final J2clBuildStep step,
                                final J2clLinePrinter logger) throws Exception {
        final HashBuilder hash = HashBuilder.empty();

        this.hashDependencies(artifact, hash, logger);
        this.hashArtifactSources(artifact, logger, hash);

        artifact.setHash(hash)
                .step(J2clBuildStep.HASH);
        return J2clBuildStepResult.SUCCESS;
    }

    private void hashDependencies(final J2clDependency artifact,
                                  final HashBuilder hash,
                                  final J2clLinePrinter logger) {
        final Set<J2clDependency> dependencies = artifact.dependencies();
        logger.printLine(dependencies.size() + " Dependencies (transitives not counted)");
        logger.indent();

        // printLine dependency and their hashes...this assumes dependencies have had their hash code already computed.

        for (final J2clDependency dependency : dependencies) {
            logger.printLine(dependency.toString());
            if(dependency.isIncluded()) {
                logger.indent();

                final String dependencyHash = dependency.hashOrFail();
                {
                    hash.append(dependencyHash);

                    final J2clPath directory = dependency.directory();
                    logger.printLine(directory.toString());
                    logger.printIndentedLine(dependencyHash);
                }

                logger.printLine(dependencyHash);
                logger.outdent();
            }
        }

        logger.printEndOfList();
        logger.outdent();
        logger.emptyLine();
    }

    private void hashArtifactSources(final J2clDependency artifact,
                                     final J2clLinePrinter logger,
                                     final HashBuilder hash) throws IOException {
        final List<J2clPath> compileSourcesRoot = artifact.sourcesRoot();

        if (compileSourcesRoot.isEmpty()) {
            final J2clPath file = artifact.artifactFile()
                    .orElseThrow(() -> new IllegalStateException("File missing from " + CharSequences.quote(artifact.coords().toString())));
            try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.file().toURI()), Collections.emptyMap())) {
                this.hashCompileSourceRoots(hash, zip.getRootDirectories(), logger);
            }
        } else {
            this.hashCompileSourceRoots(hash,
                    compileSourcesRoot.stream().map(J2clPath::path).collect(Collectors.toList()),
                    logger);
        }
    }

    private void hashCompileSourceRoots(final HashBuilder hash,
                                        final Iterable<Path> roots,
                                        final J2clLinePrinter logger) throws IOException {
        logger.printLine(Lists.newArrayList(roots).size() + " Source root(s)");
        logger.indent();

        for (final Path root : roots) {
            this.hashCompileSourceRoot(hash, root, logger);
        }

        logger.printEndOfList();
        logger.outdent();
    }

    private void hashCompileSourceRoot(final HashBuilder hash,
                                       final Path root,
                                       final J2clLinePrinter logger) throws IOException {
        logger.printLine(root.toString());
        logger.indent();

        Files.walkFileTree(root, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path path,
                                                     final BasicFileAttributes basicFileAttributes) {
                logger.printLine(path.getFileName() + " (dir)");
                logger.indent();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path path,
                                             final BasicFileAttributes basicFileAttributes) throws IOException {
                hash.append(Files.readAllBytes(path));
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
