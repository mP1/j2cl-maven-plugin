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

package walkingkooka.j2cl.maven.transpile;

import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clArtifact;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.List;
import java.util.Optional;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
public final class J2clTask2clTranspiler<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTask2clTranspiler<>();
    }

    private J2clTask2clTranspiler() {
        super();
    }

    @Override
    public J2clTaskResult execute(final J2clArtifact artifact,
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
    public J2clTaskResult executeWithDirectory(final J2clArtifact artifact,
                                               final J2clTaskDirectory directory,
                                               final C context,
                                               final TreeLogger logger) throws Exception {
        final List<J2clPath> sourceRoots = this.sourceRoots(
                artifact,
                context
        );
        final List<J2clPath> classpath = this.classpath(artifact);

        return J2clTranspiler.execute(
                classpath,
                sourceRoots,
                directory.output().absentOrFail(),
                logger
        ) ?
                J2clTaskResult.SUCCESS :
                J2clTaskResult.FAILED;
    }

    private List<J2clPath> sourceRoots(final J2clArtifact artifact,
                                       final J2clMavenContext context) {
        final J2clTaskKind first = J2clTaskKind.SHADE_JAVA_SOURCE;
        final J2clTaskKind second = J2clTaskKind.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE;

        final List<J2clPath> sourceRoots;

        final Optional<J2clPath> shaded = output(artifact, first);
        if (shaded.isPresent()) {
            sourceRoots = Lists.of(shaded.get());
        } else {
            final Optional<J2clPath> stripped = output(artifact, second);
            if (stripped.isPresent()) {
                sourceRoots = Lists.of(stripped.get());
            } else {
                sourceRoots = context.sources(artifact);
                if (sourceRoots.isEmpty()) {
                    throw new IllegalStateException("Missing " + first + "/output, " + second + "/output AND source root for " + artifact);
                }
            }
        }

        return sourceRoots;
    }

    private List<J2clPath> classpath(final J2clArtifact artifact) {
        final List<J2clPath> classpath = Lists.array();

        // only transpile if class required incl annotations, but not ignored or jre........................................

        for (final J2clArtifact dependency : artifact.dependencies()) {
            if (dependency.isAnnotationClassFiles()) {
                classpath.add(dependency.artifactFileOrFail());
                continue;
            }

            if (dependency.isAnnotationProcessor()) {
                continue;
            }

            if (dependency.isIgnored()) {
                continue;
            }

            if (dependency.isJreBootstrapClassFiles()) {
                classpath.add(dependency.artifactFileOrFail());
                continue;
            }

            if (dependency.isJreClassFiles()) {
                classpath.add(dependency.artifactFileOrFail());
                continue;
            }

            if (dependency.isClasspathRequired()) {
                final Optional<J2clPath> shadeClassFiles = output(dependency, J2clTaskKind.SHADE_CLASS_FILES);
                if (shadeClassFiles.isPresent()) {
                    classpath.add(shadeClassFiles.get());
                    continue;
                }
                final Optional<J2clPath> compileGwtIncompatibleStripped = output(dependency, J2clTaskKind.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE);
                compileGwtIncompatibleStripped.map(classpath::add);
            }

            // shouldnt happen
        }

        return classpath;
    }

    private static Optional<J2clPath> output(final J2clArtifact artifact,
                                             final J2clTaskKind kind) {
        return artifact.taskDirectory(kind)
                .output()
                .exists();
    }
}
