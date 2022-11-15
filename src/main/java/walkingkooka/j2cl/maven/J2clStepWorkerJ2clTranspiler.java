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

import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.log.TreeLogger;

import java.util.Optional;
import java.util.Set;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
final class J2clStepWorkerJ2clTranspiler extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJ2clTranspiler();
    }

    private J2clStepWorkerJ2clTranspiler() {
        super();
    }

    @Override J2clStepResult execute1(final J2clDependency artifact,
                                      final J2clStepDirectory directory,
                                      final TreeLogger logger) throws Exception {
        logger.line("Preparing...");

        final J2clPath sourceRoot = this.sourceRoot(artifact);
        logger.path("Source path(s)", sourceRoot);

        final Set<J2clPath> classpath = this.classpath(artifact);

        return J2clTranspiler.execute(classpath,
                sourceRoot,
                directory.output().absentOrFail(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }

    private J2clPath sourceRoot(final J2clDependency artifact) {
        final J2clStep first = J2clStep.SHADE_JAVA_SOURCE;
        final J2clStep second = J2clStep.GWT_INCOMPATIBLE_STRIP;
        final J2clStep third = J2clStep.UNPACK;

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
                final Optional<J2clPath> shadeClassFiles = output(dependency, J2clStep.SHADE_CLASS_FILES);
                if (shadeClassFiles.isPresent()) {
                    classpath.add(shadeClassFiles.get());
                    continue;
                }
                final Optional<J2clPath> compileGwtIncompatibleStripped = output(dependency, J2clStep.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED);
                compileGwtIncompatibleStripped.map(classpath::add);
            }

            // shouldnt happen
        }

        return classpath;
    }

    private static Optional<J2clPath> output(final J2clDependency dependency, final J2clStep step) {
        return dependency.step(step)
                .output()
                .exists();
    }
}
