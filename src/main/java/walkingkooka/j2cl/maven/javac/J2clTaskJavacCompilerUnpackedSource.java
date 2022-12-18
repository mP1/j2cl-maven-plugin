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

package walkingkooka.j2cl.maven.javac;

import walkingkooka.collect.list.Lists;
import walkingkooka.j2cl.maven.J2clArtifact;
import walkingkooka.j2cl.maven.J2clMavenContext;
import walkingkooka.j2cl.maven.J2clPath;
import walkingkooka.j2cl.maven.J2clSourcesKind;
import walkingkooka.j2cl.maven.J2clTask;
import walkingkooka.j2cl.maven.J2clTaskDirectory;
import walkingkooka.j2cl.maven.J2clTaskKind;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.CharSequences;

import java.nio.file.Files;
import java.util.List;

/**
 * Compiles the java source to the target {@link J2clTaskDirectory#output()}, with annotation processors enabled.
 */
public final class J2clTaskJavacCompilerUnpackedSource<C extends J2clMavenContext> extends J2clTaskJavacCompiler<C> {

    /**
     * Singleton
     */
    public static <C extends J2clMavenContext> J2clTask<C> instance() {
        return new J2clTaskJavacCompilerUnpackedSource<>();
    }

    /**
     * Use singleton
     */
    private J2clTaskJavacCompilerUnpackedSource() {
        super();
    }

    @Override
    J2clTaskKind sourceTask() {
        return J2clTaskKind.UNPACK;
    }

    @Override
    List<J2clTaskKind> compileTask() {
        return Lists.of(J2clTaskKind.JAVAC_COMPILE);
    }

    @Override
    boolean shouldRunAnnotationProcessors() {
        return true;
    }

    /**
     * Always add the dependency jar file.
     */
    @Override
    J2clPath selectClassFiles(final J2clArtifact dependency) {
        return dependency.artifactFileOrFail();
    }

    /**
     * If executing a test fixup the name of the generated javascript file.
     */
    @Override
    void postCompile(final J2clArtifact artifact,
                     final J2clTaskDirectory directory,
                     final C context,
                     final TreeLogger logger) throws Exception {
        if (false == artifact.isDependency() && J2clSourcesKind.TEST == context.sourcesKind()) {
            this.postCompileJunitProcessorFix(
                    directory,
                    context,
                    logger
            );
        }
    }

    private void postCompileJunitProcessorFix(final J2clTaskDirectory directory,
                                              final C context,
                                              final TreeLogger logger) throws Exception {
        logger.indent();
        {
            logger.line("Junit processor post fixup");
            logger.indent();
            {
                final J2clPath output = directory.output();

                for (final String testSuiteClassName : context.entryPoints()) {
                    logger.line(testSuiteClassName);

                    final String testClassName = extractTestClassName(testSuiteClassName);
                    final J2clPath generatedFilename = output.testAdapterSuiteGeneratedFilename(testClassName);
                    final J2clPath correctFilename = output.testAdapterSuiteCorrectFilename(testClassName);

                    Files.copy(generatedFilename.path(), correctFilename.path());
                }
            }
            logger.outdent();
        }
        logger.outdent();
    }

    // 'javatests.org.gwtproject.timer.client.TimerJ2clTest_AdapterSuite' -> org.gwtproject.timer.client.TimerJ2clTest
    public static String extractTestClassName(final String typeName) {
        return CharSequences.subSequence(
                typeName, "javatests.".length(), -"_AdapterSuite".length()
        ).toString();
    }
}
