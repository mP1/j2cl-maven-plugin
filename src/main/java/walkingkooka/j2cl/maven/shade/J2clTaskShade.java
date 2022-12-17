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

package walkingkooka.j2cl.maven.shade;

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
import walkingkooka.text.CharSequences;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * If the dependency source has a shade file, create an output directory with selected shaded class files combined
 * with the other class files changed.
 */
abstract class J2clTaskShade<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Package private to limit sub classing.
     */
    J2clTaskShade() {
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
    public final J2clTaskResult executeWithDirectory(final J2clDependency artifact,
                                                     final J2clTaskDirectory directory,
                                                     final C context,
                                                     final TreeLogger logger) throws Exception {
        final J2clTaskResult result;

        if (artifact.isIgnored()) {
            result = J2clTaskResult.SKIPPED;
        } else {
            logger.line(J2clPath.SHADE_FILE);
            logger.indent();
            {
                final Map<String, String> shadeMappings = artifact.shadeMappings();

                if (!shadeMappings.isEmpty()) {
                    this.copyAndShade(
                            artifact,
                            artifact.taskDirectory(this.kind()).output(),
                            shadeMappings,
                            directory.output(),
                            context,
                            logger
                    );
                    result = J2clTaskResult.SUCCESS;
                } else {
                    logger.line("Not found");
                    result = J2clTaskResult.SKIPPED;
                }
            }
            logger.outdent();
        }

        return result;
    }

    abstract J2clTaskKind kind();

    /**
     * Performs two copy passes, the first will shade any files during the copy process, the second will simply
     * copy the files to the destination.
     */
    private void copyAndShade(final J2clDependency artifact,
                              final J2clPath root,
                              final Map<String, String> shade,
                              final J2clPath output,
                              final C context,
                              final TreeLogger logger) throws Exception {
        final BiFunction<byte[], J2clPath, byte[]> contentShader = (content, path) -> shade(content, shade);
        final Predicate<Path> filter = this.fileExtensionFilter();

        final Set<J2clPath> files = root.gatherFiles(J2clPath.ALL_FILES.and(filter));

        final Set<J2clPath> possibleFiles = Sets.sorted();
        possibleFiles.addAll(files);

        final Set<J2clPath> nonShadedFiles = Sets.sorted();
        nonShadedFiles.addAll(files);

            for (final Entry<String, String> mapping : shade.entrySet()) {
                final String find = mapping.getKey();
                final String replace = mapping.getValue();

                final J2clPath shadeDirectory = replace.isEmpty() ?
                        output :
                        output.append(replace.replace('.', File.separatorChar));

                final Set<J2clPath> shadedFiles = Sets.sorted();
                final J2clPath shadedRoot = root.append(find.replace('.', File.separatorChar));

                // filter only files belonging to and under shade source root
                possibleFiles.stream()
                        .filter(f -> f.path().startsWith(shadedRoot.path()))
                        .forEach(shadedFiles::add);

                possibleFiles.removeAll(shadedFiles);

                // else files will be copied below
                if (find.equals(replace)) {
                    logger.line("Skipping shade package " + CharSequences.quote(find));
                } else {
                    logger.line("Shading package from " + CharSequences.quote(find) + " to " + CharSequences.quote(replace));
                    logger.indent();
                    {
                        // copy and shade java source and copy other files to output.
                        shadeDirectory.copyFiles(
                                shadedRoot,
                                shadedFiles,
                                contentShader
                        );

                        logger.paths(
                                "",
                                shadedFiles,
                                TreeFormat.TREE
                        );

                        nonShadedFiles.removeAll(shadedFiles);
                    }
                    logger.outdent();
                }
            }

            logger.line("Copying other files");
            logger.indent();
        {

            // copy all other files verbatim.
            output.copyFiles(
                    root,
                    nonShadedFiles,
                    contentShader
            );

            logger.paths(
                    "",
                    nonShadedFiles,
                    TreeFormat.TREE
            );

            this.postCopyAndShade(
                    artifact,
                    output,
                    context,
                    logger
            );
        }
            logger.outdent();
    }

    /**
     * A filter that only tests the file extension. It does not test if the file actually exists.
     */
    abstract Predicate<Path> fileExtensionFilter();

    /**
     * Reads the file and shades the source text or class file type references.
     */
    abstract byte[] shade(final byte[] content,
                          final Map<String, String> mappings);

    /**
     * This is invoked after and files are copy and shaded, the primary use case is copying javascript files
     * after java files have been shaded.
     */
    abstract void postCopyAndShade(final J2clDependency artifact,
                                   final J2clPath output,
                                   final C context,
                                   final TreeLogger logger) throws Exception;
}
