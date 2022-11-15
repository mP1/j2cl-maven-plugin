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

import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;
import java.util.Optional;

/**
 * Unpacks the source from the sources artifact (jar with sources) and if no java files are present tries
 * the binary (jar) to {@link J2clStepDirectory#output()}. If no java source files are present processing of this
 * artifact is aborted and no attempt will be made to transpile java to javascript.
 */
final class J2clStepWorkerUnpack extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerUnpack();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerUnpack() {
        super();
    }

    @Override
    J2clStepResult execute1(final J2clDependency artifact,
                            final J2clStepDirectory directory,
                            final TreeLogger logger) throws Exception {
        J2clStepResult result;

        final J2clPath dest = directory.output().absentOrFail();
        logger.path("Destination", dest);
        {
            boolean filesFound = this.extractSourceRoots(artifact, dest, logger);

            if (false == filesFound) {
                // if no source is available unpack the binary might be a jszip.
                final Optional<J2clPath> archive = artifact.artifactFile();
                if (archive.isPresent()) {
                    final J2clPath path = archive.get();
                    logger.path("Archive", path);
                    logger.indent();
                    {
                        filesFound = archive.get()
                                .extractArchiveFiles(J2clPath.WITHOUT_META_INF,
                                        dest,
                                        logger)
                                .size() > 0;
                    }
                    logger.outdent();
                }
            }

            if(filesFound) {
                logger.line("Source files found, transpiling will happen");
                result = J2clStepResult.SUCCESS;
            } else {
                logger.line("No source files found, transpiling will not be attempted");
                result = J2clStepResult.ABORTED;
            }
        }

        return result;
    }

    private boolean extractSourceRoots(final J2clDependency artifact,
                                       final J2clPath dest,
                                       final TreeLogger logger) throws Exception {
        boolean filesFound = false;

        final List<J2clPath> sourceRoots = artifact.sourcesRoot();
        logger.paths("Source root(s)", sourceRoots, TreeFormat.TREE);

        logger.line("Unpacking...");
        logger.indent();
        {

            for (final J2clPath source : sourceRoots) {
                logger.line(source.toString());
                logger.indent();
                {
                    if (source.isTestAnnotation()) {
                        logger.line("// test annotations source skipped");
                        continue;
                    }

                    // dont want to copy test-annotations will contain the generated class by any annotation processor.
                    filesFound |= source.isFile() ?
                            source.extractArchiveFiles(J2clPath.WITHOUT_META_INF,
                                    dest,
                                    logger)
                                    .size() > 0 :
                            dest.copyFiles(source,
                                    source.gatherFiles(J2clPath.ALL_FILES),
                                    J2clPath.COPY_FILE_CONTENT_VERBATIM,
                                    logger).size() > 0;
                }
                logger.outdent();
            }
        }
        logger.outdent();
        logger.endOfList();

        return filesFound;
    }
}
