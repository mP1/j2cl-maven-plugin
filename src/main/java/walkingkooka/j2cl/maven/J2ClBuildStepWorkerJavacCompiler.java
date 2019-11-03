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

import walkingkooka.collect.list.Lists;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compiles the java source from sources and the given target.
 */
abstract class J2ClBuildStepWorkerJavacCompiler extends J2ClBuildStepWorker2 {

    /**
     * Package private to limit sub classing.
     */
    J2ClBuildStepWorkerJavacCompiler() {
        super();
    }

    @Override
    final J2clBuildStepResult execute0(final J2clDependency artifact,
                                       final J2clStepDirectory directory,
                                       final J2clLinePrinter logger) throws Exception {
        // in the end only the project is compiled, all other dependencies remain untouched.
        final List<J2clPath> sourceRoots = this.sourceRoots(artifact);
        final List<J2clPath> javaSourceFiles = J2clPath.findFiles(sourceRoots,
                J2clPath.JAVA_FILES);//, // files being compiled

        J2clBuildStepResult result;
        if (javaSourceFiles.size() > 0) {
            final Set<J2clPath> classpath = artifact.dependenciesIncludingTransitives()
                    .stream()
                    .filter(a -> false == a.isJreBinary())
                    .flatMap(a -> a.artifactFile().stream())
                    .collect(Collectors.toCollection(Sets::sorted));

            result = JavacCompiler.execute(bootstrap(),
                    classpath,
                    javaSourceFiles,
                    sourceRoots,
                    directory.output().emptyOrFail(),
                    logger) ?
                    J2clBuildStepResult.SUCCESS :
                    J2clBuildStepResult.FAILED;
        } else {
            if (artifact.isDependency()) {
                logger.printIndentedLine("No files found - javac aborted.");
                directory.aborted().emptyOrFail();
                result = J2clBuildStepResult.ABORTED;
            } else {
                logger.printIndentedLine("No files found - javac skipped.");
                directory.skipped().emptyOrFail();
                result = J2clBuildStepResult.SKIPPED;
            }
        }

        return result;
    }

    private List<J2clPath> bootstrap() {
        final J2clDependency bootstrap = J2clDependency.javacBootstrap();

        return bootstrap.artifactFile()
                .map(Lists::of)
                .orElseThrow(() -> new IllegalStateException("javac Bootstrap artifact " + CharSequences.quote(bootstrap.coords().toString()) + " missing"));
    }

    /**
     * Use the gwt-incompatible-strip step output as the source roots input to the compiler.
     */
    private List<J2clPath> sourceRoots(final J2clDependency artifact) {
        return artifact.step(this.sourcesStep())
                .output()
                .exists()
                .map(Lists::of)
                .orElse(Lists.empty());
    }

    /**
     * A previous step that has source in its output ready for compiling
     */
    abstract J2clBuildStep sourcesStep();
}
