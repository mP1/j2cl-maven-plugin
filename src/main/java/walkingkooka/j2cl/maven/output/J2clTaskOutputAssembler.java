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

package walkingkooka.j2cl.maven.output;

import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeFormat;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.Set;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
public final class J2clTaskOutputAssembler<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskOutputAssembler<>();
    }

    private J2clTaskOutputAssembler() {
        super();
    }

    @Override
    public J2clTaskResult execute(final J2clDependency artifact,
                                  final J2clTaskKind kind,
                                  final C context,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                kind,
                context,
                logger
        );
    }

    @Override
    public J2clTaskResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clTaskDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        final Set<J2clPath> closureCompileDestinationFiles = Sets.ordered();
        final Set<J2clPath> unpackPublicDestinationFiles = Sets.ordered();

        logger.line("Sources");
        logger.indent();
        {
            final J2clPath closureCompile = artifact.taskDirectory(J2clTaskKind.CLOSURE_COMPILE).output();
            final Set<J2clPath> closureCompileFiles = closureCompile.gatherFiles(J2clPath.ALL_FILES);

            logger.paths(
                    "",
                    closureCompileFiles,
                    TreeFormat.TREE
            );

            for (final J2clPath unpackPublic : context.sources(artifact)) {
                final Optional<PathMatcher> unpackPublicPathMatcher = unpackPublic.publicFiles();
                final Set<J2clPath> unpackPublicFiles;

                if (unpackPublicPathMatcher.isPresent()) {
                    unpackPublicFiles = unpackPublic.gatherFiles(
                            (p) -> unpackPublicPathMatcher.get().matches(p)
                    );

                    logger.paths(
                            "",
                            unpackPublicFiles,
                            TreeFormat.TREE
                    );
                } else {
                    unpackPublicFiles = Sets.empty();
                }

                final J2clPath destination = context.target()
                        .createIfNecessary();

                closureCompileDestinationFiles.addAll(
                        destination.copyFiles(
                                closureCompile,
                                closureCompileFiles,
                                J2clPath.COPY_FILE_CONTENT_VERBATIM
                        )
                );

                unpackPublicDestinationFiles.addAll(
                        destination.copyFiles(
                                unpackPublic,
                                unpackPublicFiles,
                                J2clPath.COPY_FILE_CONTENT_VERBATIM
                        )
                );
            }
        }
        logger.outdent();

        final Set<J2clPath> destinationFiles = Sets.sorted();
        destinationFiles.addAll(closureCompileDestinationFiles);
        destinationFiles.addAll(unpackPublicDestinationFiles);

        logger.paths(
                "Destination",
                destinationFiles,
                TreeFormat.TREE
        );

        final J2clTaskResult result;

        if (destinationFiles.isEmpty()) {
            logger.line("No files copied, transpile task likely failed with warnings that are actually errors.");
            result = J2clTaskResult.FAILED;
        } else {
            result = J2clTaskResult.SUCCESS;
        }

        return result;
    }
}
