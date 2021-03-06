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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * A {@link J2clRequest} that accompanies a build. The entry points and initial-script-filename are taken from the pom.
 */
final class J2clMojoBuildRequest extends J2clRequest {

    static J2clRequest with(final J2clPath base,
                            final J2clPath target,
                            final J2clClasspathScope scope,
                            final List<J2clArtifactCoords> classpathRequired,
                            final List<J2clArtifactCoords> ignoredDependencies,
                            final List<J2clArtifactCoords> javascriptSourceRequired,
                            final CompilationLevel level,
                            final Map<String, String> defines,
                            final Set<String> externs,
                            final List<String> entryPoints,
                            final Set<ClosureFormattingOption> formatting,
                            final J2clPath initialScriptFilename,
                            final Set<String> javaCompilerArguments,
                            final LanguageMode languageOut,
                            final Optional<String> sourceMaps,
                            final J2clMavenMiddleware middleware,
                            final ExecutorService executor,
                            final J2clLogger logger) {
        return new J2clMojoBuildRequest(base,
                target,
                scope,
                classpathRequired,
                ignoredDependencies,
                javascriptSourceRequired,
                level,
                defines,
                externs,
                entryPoints,
                formatting,
                initialScriptFilename,
                javaCompilerArguments,
                languageOut,
                sourceMaps,
                middleware,
                executor,
                logger);
    }

    private J2clMojoBuildRequest(final J2clPath base,
                                 final J2clPath target,
                                 final J2clClasspathScope scope,
                                 final List<J2clArtifactCoords> classpathRequired,
                                 final List<J2clArtifactCoords> ignoredDependencies,
                                 final List<J2clArtifactCoords> javascriptSourceRequired,
                                 final CompilationLevel level,
                                 final Map<String, String> defines,
                                 final Set<String> externs,
                                 final List<String> entryPoints,
                                 final Set<ClosureFormattingOption> formatting,
                                 final J2clPath initialScriptFilename,
                                 final Set<String> javaCompilerArguments,
                                 final LanguageMode languageOut,
                                 final Optional<String> sourceMaps,
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
        this.entryPoints = entryPoints;
        this.initialScriptFilename = initialScriptFilename;
    }

    @Override
    J2clSourcesKind sourcesKind() {
        return J2clSourcesKind.SRC;
    }

    @Override
    List<String> entryPoints() {
        return this.entryPoints;
    }

    private final List<String> entryPoints;

    @Override
    J2clPath initialScriptFilename() {
        return this.initialScriptFilename;
    }

    private final J2clPath initialScriptFilename;

    @Override
    HashBuilder computeHash(final Set<String> hashItemNames) {
        final HashBuilder hash = this.computeHash0(hashItemNames);

        this.entryPoints.forEach(e -> {
            hashItemNames.add("entry-points: " + e);
            hash.append(e);
        });

        final String initialScriptFilename = this.initialScriptFilename.toString();
        hashItemNames.add("initial-script-filename: " + initialScriptFilename);
        hash.append(initialScriptFilename);

        return hash;
    }

    @Override
    List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers() {
        throw new UnsupportedOperationException();
    }

    @Override
    BrowserLogLevel browserLogLevel() {
        throw new UnsupportedOperationException();
    }

    @Override
    int testTimeout() {
        throw new UnsupportedOperationException();
    }
}
