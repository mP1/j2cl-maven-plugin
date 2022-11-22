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
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepDirectory;
import walkingkooka.j2cl.maven.J2clStepResult;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.nio.file.PathMatcher;
import java.util.Optional;
import java.util.Set;

/**
 * Calls the closure compiler and assembles the final Javascript output.
 */
public final class J2clStepWorkerOutputAssembler<C extends J2clMavenContext> implements J2clStepWorker<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clStepWorker<C> instance() {
        return new J2clStepWorkerOutputAssembler<>();
    }

    private J2clStepWorkerOutputAssembler() {
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
        final J2clPath closureCompile = artifact.step(J2clStep.CLOSURE_COMPILE).output();
        final Set<J2clPath> closureCompileFiles = closureCompile.gatherFiles(J2clPath.ALL_FILES);

        final J2clPath unpackPublic = artifact.step(J2clStep.UNPACK).output();
        final Optional<PathMatcher> unpackPublicPathMatcher = unpackPublic.publicFiles();
        final Set<J2clPath> unpackPublicFiles =
                unpackPublicPathMatcher.isPresent() ?
                        unpackPublic.gatherFiles((p) -> unpackPublicPathMatcher.get().matches(p)) :
                        Sets.empty();

        logger.path("Source", closureCompile);
        if (unpackPublicPathMatcher.isPresent()) {
            logger.path("Source", unpackPublic);
        }

        final J2clPath destination = context.target()
                .createIfNecessary();

        logger.line("Destination");

        int copyCount = destination.copyFiles(
                closureCompile,
                closureCompileFiles,
                J2clPath.COPY_FILE_CONTENT_VERBATIM,
                logger
        ).size();

        if (unpackPublicPathMatcher.isPresent()) {
            copyCount += destination.copyFiles(
                    unpackPublic,
                    unpackPublicFiles,
                    J2clPath.COPY_FILE_CONTENT_VERBATIM,
                    logger
            ).size();
        }

        final J2clStepResult result;

        if (0 == copyCount) {
            logger.line("No files copied, transpile step likely failed with warnings that are actually errors.");
            result = J2clStepResult.FAILED;
        } else {
            result = J2clStepResult.SUCCESS;
        }

        return result;
    }
}
