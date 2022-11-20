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

import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clDependency;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clStep;
import walkingkooka.j2cl.maven.J2clStepWorker;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.javashader.JavaShaders;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * If the dependency source has a shade file, create an output directory with selected shaded class files combined
 * with the other class files changed.
 */
public final class J2clStepWorkerShadeJavaSource<C extends J2clMavenContext> extends J2clStepWorkerShade<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clStepWorker<C> instance() {
        return new J2clStepWorkerShadeJavaSource<>();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerShadeJavaSource() {
        super();
    }

    @Override
    J2clStep step() {
        return J2clStep.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE;
    }

    @Override
    Predicate<Path> fileExtensionFilter() {
        return J2clPath.JAVA_FILEEXTENSION;
    }

    @Override
    byte[] shade(final byte[] content,
                 final Map<String, String> shadings) {
        return JavaShaders.javaFilePackageShader(Charset.defaultCharset())
                .apply(content, shadings);
    }

    /**
     * This solves the problem of javascript files that will be present in {@link J2clStep#JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE}
     * that are generated by annotation process such as during tests.
     */
    @Override
    void postCopyAndShade(final J2clDependency artifact,
                          final J2clPath output,
                          final TreeLogger logger) throws Exception {
        copyJavascriptFiles(Lists.of(artifact.step(J2clStep.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE).output(), artifact.step(J2clStep.UNPACK).output()),
                output,
                logger);
    }

    private static void copyJavascriptFiles(final List<J2clPath> sourceRoots,
                                            final J2clPath output,
                                            final TreeLogger logger) throws IOException {
        logger.line("Copying *.js");
        logger.indent();
        {
            for (final J2clPath sourceRoot : sourceRoots) {
                logger.line(sourceRoot.toString());
                logger.indent();
                {
                    final Set<J2clPath> copy = sourceRoot.gatherFiles(J2clPath.JAVASCRIPT_FILES);
                    output.copyFiles(sourceRoot,
                            copy,
                            J2clPath.COPY_FILE_CONTENT_VERBATIM,
                            logger);
                }
                logger.outdent();
            }
        }
        logger.outdent();
    }
}