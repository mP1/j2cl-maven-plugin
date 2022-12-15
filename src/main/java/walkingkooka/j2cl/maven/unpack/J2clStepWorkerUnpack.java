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

package walkingkooka.j2cl.maven.unpack;

import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepDirectory;
import walkingkooka.j2cl.maven.J2clStepResult;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unpacks the source from the sources artifact (jar with sources) and if no java files are present tries
 * the binary (jar) to {@link J2clStepDirectory#output()}. If no java source files are present processing of this
 * artifact is aborted and no attempt will be made to transpile java to javascript.
 */
public final class J2clStepWorkerUnpack<C extends J2clMavenContext> implements J2clStepWorker<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clStepWorker<C> instance() {
        return new J2clStepWorkerUnpack<>();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerUnpack() {
        super();
    }

    @Override
    public J2clStepResult execute(final J2clDependency artifact,
                                  final J2clStep step,
                                  final C context,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                step,
                context,
                logger
        );
    }

    @Override
    public J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clStepDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        J2clStepResult result;

        final J2clPath dest = directory.output().absentOrFail();

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

        if (filesFound) {
            logger.path("Destination", dest);

            logger.line("Source files found, transpiling will happen");
            result = J2clStepResult.SUCCESS;
        } else {
            logger.line("No source files found, transpiling will not be attempted");
            result = J2clStepResult.ABORTED;
        }

        return result;
    }

    private boolean extractSourceRoots(final J2clDependency artifact,
                                       final J2clPath dest,
                                       final TreeLogger logger) throws Exception {
        boolean filesFound = false;

        final List<J2clPath> sourceRoots = artifact.sourcesRoot();
        logger.paths(
                "Source root(s)",
                sourceRoots,
                TreeFormat.TREE
        );

        logger.line("Unpacking...");
        logger.indent();
        {
            for (final J2clPath source : sourceRoots) {
                if (source.isTestAnnotation()) {
                    logger.line(source.toString());
                    logger.indent();
                    {
                        logger.line("// test annotations source skipped");
                    }
                    logger.outdent();
                } else {
                    // dont want to copy test-annotations will contain the generated class by any annotation processor.
                    if (source.isFile()) {
                        logger.line(source.toString());
                        logger.indent();
                        {
                            final Set<J2clPath> extractedFiles = source.extractArchiveFiles(
                                    J2clPath.WITHOUT_META_INF,
                                    dest,
                                    logger
                            );

                            filesFound |= extractedFiles.size() > 0;
                        }
                        logger.outdent();

                    } else {
                        final Set<J2clPath> sourceFiles = source.gatherFiles(J2clPath.ALL_FILES);

                        dest.copyFiles(
                                source,
                                sourceFiles,
                                J2clPath.COPY_FILE_CONTENT_VERBATIM
                        );

                        logger.paths(
                                "",
                                sourceFiles,
                                TreeFormat.TREE
                        );

                        filesFound |= sourceFiles.size() > 0;
                    }
                }
            }
        }
        logger.outdent();
        logger.endOfList();

        return filesFound;
    }
}
