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
import walkingkooka.text.CharSequences;

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
 * Unpacks the source from the sources artifact (jar with sources) and if no java files are present tries
 * the binary (jar) to {@link J2clStepDirectory#output()}. If no java source files are present processing of this
 * artifact is aborted and no attempt will be made to transpile java to javascript.
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
        
        {
            boolean javaFilesFound = this.extractSourceRoots(artifact, dest, logger);

            if (false == javaFilesFound) {
                if (artifact.isJreBinary()) {
                    throw new J2clException("JRE sources " + CharSequences.quote(artifact.coords().toString()) + " missing.");
                }

                // if no source is available unpack the binary might be a jszip.
                final Optional<J2clPath> archive = artifact.artifactFile();
                if (archive.isPresent()) {
                    final J2clPath path = archive.get();
                    logger.printIndented("Archive", path);
                    logger.indent();
                    javaFilesFound = this.extractArchiveFiles(archive.get().path(), dest, logger);
                    logger.outdent();
                }
            }

            if(javaFilesFound) {
                if(artifact.isJre()) {
                    logger.printLine("JRE artifact, Java sources found will be ignored, transpiling will not be attempted");
                    result = J2clBuildStepResult.ABORTED;
                } else {
                    logger.printLine("Java source found, transpiling will happen");
                    result = J2clBuildStepResult.SUCCESS;
                }
            } else {
                logger.printLine("No java source found, transpiling will not be attempted");
                result = J2clBuildStepResult.SUCCESS;
            }
        }

        return result;
    }

    private boolean extractSourceRoots(final J2clDependency artifact,
                                       final J2clPath dest,
                                       final J2clLinePrinter logger) throws Exception {
        boolean javaFilesFound = false;

        final List<J2clPath> sources = artifact.sourcesRoot();
        logger.printIndented("Source root(s)", sources);
        logger.indent();
        {
            for (final J2clPath source : sources) {
                logger.indent();
                logger.printLine(source.toString());
                {
                    final Path sourcePath = source.path();
                    javaFilesFound |= source.isFile() ?
                            this.extractArchiveFiles(sourcePath, dest, logger) :
                            this.gatherFilesSortThenCopy(sourcePath, dest, logger);
                }
                logger.outdent();
            }
            logger.printEndOfList();
        }
        logger.outdent();

        return javaFilesFound;
    }

    private boolean extractArchiveFiles(final Path archive,
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
    private boolean gatherFilesSortThenCopy(final Path source,
                                            final J2clPath target,
                                            final J2clLinePrinter logger) throws IOException {
        final Set<J2clPath> files = J2clPath.with(source).gatherFiles();
        if (files.isEmpty()) {
            logger.printIndentedLine("No files");
        } else {
            this.extractFiles(source, target, files, logger);
        }

        return files.stream()
                .anyMatch(J2clPath::isJavaFile);
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
