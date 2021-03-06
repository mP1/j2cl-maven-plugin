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

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import walkingkooka.collect.list.Lists;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * A {@link J2clRequest} that accompanies a test. The entry points and initial-script-filename are NOT taken from the pom,
 * but rather generated by {@link J2clMojoTest} and this request is reused over multiple tests.
 */
final class J2clMojoTestRequest extends J2clRequest {

    static J2clMojoTestRequest with(final J2clPath base,
                                    final J2clPath target,
                                    final J2clClasspathScope scope,
                                    final List<J2clArtifactCoords> classpathRequired,
                                    final List<J2clArtifactCoords> ignoredDependencies,
                                    final List<J2clArtifactCoords> javascriptSourceRequired,
                                    final CompilationLevel level,
                                    final Map<String, String> defines,
                                    final Set<String> externs,
                                    final Set<ClosureFormattingOption> formatting,
                                    final Set<String> javaCompilerArguments,
                                    final LanguageMode languageOut,
                                    final Optional<String> sourceMaps,
                                    final List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers,
                                    final BrowserLogLevel browserLogLevel,
                                    final String testClassName,
                                    final int testTimeout,
                                    final J2clMavenMiddleware middleware,
                                    final ExecutorService executor,
                                    final J2clLogger logger) {
        return new J2clMojoTestRequest(base,
                target,
                scope,
                classpathRequired,
                ignoredDependencies,
                javascriptSourceRequired,
                level,
                defines,
                externs,
                formatting,
                javaCompilerArguments,
                languageOut,
                sourceMaps,
                browsers,
                browserLogLevel,
                testClassName,
                testTimeout,
                middleware,
                executor,
                logger);
    }

    private J2clMojoTestRequest(final J2clPath base,
                                final J2clPath target,
                                final J2clClasspathScope scope,
                                final List<J2clArtifactCoords> classpathRequired,
                                final List<J2clArtifactCoords> ignoredDependencies,
                                final List<J2clArtifactCoords> javascriptSourceRequired,
                                final CompilationLevel level,
                                final Map<String, String> defines,
                                final Set<String> externs,
                                final Set<ClosureFormattingOption> formatting,
                                final Set<String> javaCompilerArguments,
                                final LanguageMode languageOut,
                                final Optional<String> sourceMaps,
                                final List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers,
                                final BrowserLogLevel browserLogLevel,
                                final String testClassName,
                                final int testTimeout,
                                final J2clMavenMiddleware middleware,
                                final ExecutorService executor,
                                final J2clLogger logger) {
        super(base,
                target,
                scope,
                classpathRequired,
                ignoredDependencies,
                javascriptSourceRequired,
                level,
                defines,
                externs,
                formatting,
                javaCompilerArguments,
                languageOut,
                sourceMaps,
                middleware,
                executor,
                logger);
        this.browsers = browsers;
        this.browserLogLevel = browserLogLevel;
        this.testClassName = testClassName;
        this.testTimeout = testTimeout;
    }

    @Override
    J2clSourcesKind sourcesKind() {
        return J2clSourcesKind.TEST;
    }

    @Override
    List<String> entryPoints() {
        // this is the mangled name of a javascript file produced by the junit annotation-processor.
        return Lists.of("javatests." + this.testClassName + "_AdapterSuite");
    }

    @Override
    J2clPath initialScriptFilename() {
        return this.project.directory()
                .append(J2clStep.CLOSURE_COMPILER.directoryName())
                .output()
                .append(this.testClassName + ".js");
    }

    @Override
    HashBuilder computeHash(final Set<String> hashItemNames) {
        final HashBuilder hash = this.computeHash0(hashItemNames);

        // no need to include browser or testTimeout in hash as these do no affect generated js
        hashItemNames.add("test-classname: " + this.testClassName);
        hash.append(this.testClassName);

        return hash;
    }

    J2clMojoTestRequest setProject(final J2clDependency project) {
        this.project = project;

        return this;
    }

    private J2clDependency project;

    @Override
    List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers() {
        return this.browsers;
    }

    private final List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers;

    @Override
    BrowserLogLevel browserLogLevel() {
        return this.browserLogLevel;
    }

    private final BrowserLogLevel browserLogLevel;

    private final String testClassName;

    @Override
    int testTimeout() {
        return this.testTimeout;
    }

    int testTimeout;
}
