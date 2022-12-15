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

import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.J2clTaskResult;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.Optional;
import java.util.Set;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
public final class J2clTaskWorkerJ2clTranspiler<C extends J2clMavenContext> implements J2clTask<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskWorkerJ2clTranspiler<>();
    }

    private J2clTaskWorkerJ2clTranspiler() {
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
        final J2clPath sourceRoot = this.sourceRoot(artifact);
        final Set<J2clPath> classpath = this.classpath(artifact);

        return J2clTranspiler.execute(classpath,
                sourceRoot,
                directory.output().absentOrFail(),
                logger) ?
                J2clTaskResult.SUCCESS :
                J2clTaskResult.FAILED;
    }

    private J2clPath sourceRoot(final J2clDependency artifact) {
        final J2clTaskKind first = J2clTaskKind.SHADE_JAVA_SOURCE;
        final J2clTaskKind second = J2clTaskKind.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE;
        final J2clTaskKind third = J2clTaskKind.UNPACK;

        final J2clPath sourceRoot;

        final Optional<J2clPath> shaded = output(artifact, first);
        if (shaded.isPresent()) {
            sourceRoot = shaded.get();
        } else {
            final Optional<J2clPath> stripped = output(artifact, second);
            if (stripped.isPresent()) {
                sourceRoot = stripped.get();
            } else {
                final Optional<J2clPath> unpack = output(artifact, third);
                if (unpack.isPresent()) {
                    sourceRoot = unpack.get();
                } else {
                    throw new IllegalStateException("Missing " + first + ", " + second + ", " + third + " output for " + artifact);
                }
            }
        }

        return sourceRoot;
    }

    private Set<J2clPath> classpath(final J2clDependency artifact) {
        final Set<J2clPath> classpath = Sets.ordered();

        // only transpile if class required incl annotations, but not ignored or jre........................................

        for (final J2clDependency dependency : artifact.dependencies()) {
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

    private static Optional<J2clPath> output(final J2clDependency dependency,
                                             final J2clTaskKind kind) {
        return dependency.task(kind)
                .output()
                .exists();
    }
}
