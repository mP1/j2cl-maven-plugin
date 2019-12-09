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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/**
 * A {@link J2clRequest} that accompanies a build. The entry points and initial-script-filename are taken from the pom.
 */
final class J2clMojoBuildRequest extends J2clRequest {

    static J2clRequest with(final J2clPath base,
                            final J2clPath target,
                            final J2clClasspathScope scope,
                            final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                            final List<J2clArtifactCoords> classpathRequired,
                            final Predicate<J2clArtifactCoords> excluded,
                            final List<J2clArtifactCoords> javascriptSourceRequired,
                            final List<J2clArtifactCoords> processingSkipped,
                            final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                            final CompilationLevel level,
                            final Map<String, String> defines,
                            final Set<String> externs,
                            final List<String> entryPoints,
                            final Set<ClosureFormattingOption> formatting,
                            final J2clPath initialScriptFilename,
                            final LanguageMode languageOut,
                            final J2clMavenMiddleware middleware,
                            final ExecutorService executor,
                            final J2clLogger logger) {
        return new J2clMojoBuildRequest(base,
                target,
                scope,
                addedDependencies,
                classpathRequired,
                excluded,
                javascriptSourceRequired,
                processingSkipped,
                replaced,
                level,
                defines,
                externs,
                entryPoints,
                formatting,
                initialScriptFilename,
                languageOut,
                middleware,
                executor,
                logger);
    }

    private J2clMojoBuildRequest(final J2clPath base,
                                 final J2clPath target,
                                 final J2clClasspathScope scope,
                                 final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                                 final List<J2clArtifactCoords> classpathRequired,
                                 final Predicate<J2clArtifactCoords> excluded,
                                 final List<J2clArtifactCoords> javascriptSourceRequired,
                                 final List<J2clArtifactCoords> processingSkipped,
                                 final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                                 final CompilationLevel level,
                                 final Map<String, String> defines,
                                 final Set<String> externs,
                                 final List<String> entryPoints,
                                 final Set<ClosureFormattingOption> formatting,
                                 final J2clPath initialScriptFilename,
                                 final LanguageMode languageOut,
                                 final J2clMavenMiddleware middleware,
                                 final ExecutorService executor,
                                 final J2clLogger logger) {
        super(base,
                target,
                scope,
                addedDependencies,
                classpathRequired,
                excluded,
                javascriptSourceRequired,
                processingSkipped,
                replaced,
                level,
                defines,
                externs,
                formatting,
                languageOut,
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
    String hash() {
        if (null == this.hash) {
            final HashBuilder hash = this.computeHash();
            this.entryPoints.forEach(hash::append);
            hash.append(this.initialScriptFilename.toString());
            this.hash = hash.toString();
        }
        return this.hash;
    }

    /**
     * Lazily computed and cached hash.
     */
    private String hash;
}