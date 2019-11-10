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

import java.util.List;
import java.util.Optional;

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
    J2clBuildStepResult execute1(final J2clDependency artifact,
                                 final J2clStepDirectory directory,
                                 final J2clLinePrinter logger) throws Exception {
        J2clBuildStepResult result;

        final J2clPath dest = directory.output().emptyOrFail();
        logger.printIndented("Destination", dest);
        
        {
            boolean javaFilesFound = this.extractSourceRoots(artifact, dest, logger);

            if (false == javaFilesFound) {
                // if no source is available unpack the binary might be a jszip.
                final Optional<J2clPath> archive = artifact.artifactFile();
                if (archive.isPresent()) {
                    final J2clPath path = archive.get();
                    logger.printIndented("Archive", path);
                    logger.indent();
                    //javaFilesFound = this.extractArchiveFiles(archive.get().path(), dest, logger);
                    javaFilesFound = archive.get().extractArchiveFiles(dest, logger).size() > 0;
                    logger.outdent();
                }
            }

            if(javaFilesFound) {
                    logger.printLine("Java source found, transpiling will happen");
                    result = J2clBuildStepResult.SUCCESS;
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

        final List<J2clPath> sourceRoots = artifact.sourcesRoot();
        logger.printIndented("Source root(s)", sourceRoots);
        logger.indent();
        {
            for (final J2clPath source : sourceRoots) {
                logger.indent();
                logger.printLine(source.toString());
                {
                    javaFilesFound |= source.isFile() ?
                            source.extractArchiveFiles(dest, logger).size() > 0 :
                            dest.copyFiles(source, source.gatherFiles(J2clPath.ALL_FILES), logger::printLine).size() > 0;
                }
                logger.outdent();
            }
            logger.printEndOfList();
        }
        logger.outdent();

        return javaFilesFound;
    }
}
