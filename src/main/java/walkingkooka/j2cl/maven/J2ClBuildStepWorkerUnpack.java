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

import walkingkooka.collect.map.Maps;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Compiles the java source to the target {@link J2clStepDirectory#output()}.
 */
final class J2ClBuildStepWorkerUnpack extends J2ClBuildStepWorker2 {

    /**
     * Singleton
     */
    static J2clBuildStepWorker instance() {
        return new J2ClBuildStepWorkerUnpack();
    }

    /**
     * Use singleton
     */
    private J2ClBuildStepWorkerUnpack() {
        super();
    }

    @Override
    J2clBuildStepResult execute0(final J2clDependency artifact,
                                 final J2clStepDirectory directory,
                                 final J2clLinePrinter logger) throws Exception {
        J2clBuildStepResult result;

        final J2clPath dest = directory.output().emptyOrFail();
        logger.printIndented("Destination", dest);

        int copied = 0;
        {
            final List<J2clPath> sources = artifact.sourcesRoot();
            logger.printIndented("Source root(s)", sources);
            logger.indent();
            for (final J2clPath source : sources) {
                logger.indent();
                logger.printLine(source.toString());
                {
                    final Path sourcePath = source.path();
                    copied += source.isFile() ?
                            this.extractArchiveFiles(sourcePath, dest, logger) :
                            this.gatherFilesSortThenCopy(sourcePath, dest, logger);
                }
                logger.outdent();
            }
            logger.printEndOfList();
            logger.outdent();

            if (0 == copied) {
                if (artifact.isJreBinary()) {
                    throw new J2clException("JRE sources missing " + artifact);
                }

                // if no source is available unpack the binary might be a jszip.
                final Optional<J2clPath> archive = artifact.artifactFile();
                if (archive.isPresent()) {
                    final J2clPath path = archive.get();
                    logger.printIndented("Archive", path);
                    logger.indent();
                    this.extractArchiveFiles(archive.get().path(), dest, logger);
                    logger.outdent();
                }

                result = J2clBuildStepResult.ABORTED;
            } else {
                result = J2clBuildStepResult.SUCCESS;
            }
        }

        return result;
    }

    private int extractArchiveFiles(final Path archive,
                                    final J2clPath target,
                                    final J2clLinePrinter logger) throws IOException {
        try (final FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + archive.toAbsolutePath().toUri()), Maps.empty())) {
            return this.gatherFilesSortThenCopy(zip.getPath("/"),
                    target,
                    logger);
        }
    }

    /**
     * First gets an alphabetical listing of all files in the given source and then proceeds to copy them to the destination.
     * This produces output that shows the files processed in alphabetical order.
     */
    private int gatherFilesSortThenCopy(final Path source,
                                        final J2clPath target,
                                        final J2clLinePrinter logger) throws IOException {
        final Set<J2clPath> files = J2clPath.with(source).gatherFiles();
        if (files.isEmpty()) {
            logger.printIndentedLine("No files");
        } else {
            this.extractFiles(source, target, files, logger);
        }

        return files.size();
    }

    private void extractFiles(final Path root,
                              final J2clPath target,
                              final Set<J2clPath> files,
                              final J2clLinePrinter logger) throws IOException {
        logger.indent();
        logger.indent();

        for (final J2clPath file : files) {
            final Path filePath = file.path();
            final Path pathInZip = root.relativize(filePath);
            final Path copyTarget = Paths.get(target.toString()).resolve(pathInZip.toString());

            Files.createDirectories(copyTarget.getParent());
            Files.copy(filePath, copyTarget);

            logger.printLine(file.toString());
        }

        logger.outdent();
        logger.printLine(files.size() + " file(s) copied");
        logger.outdent();
    }
}
