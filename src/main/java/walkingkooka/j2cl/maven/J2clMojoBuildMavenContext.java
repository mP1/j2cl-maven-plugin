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
import walkingkooka.j2cl.maven.closure.ClosureFormattingOption;
import walkingkooka.j2cl.maven.hash.HashBuilder;
import walkingkooka.j2cl.maven.log.MavenLogger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link J2clMavenContext} that accompanies a build. The entry points and initial-script-filename are taken from the pom.
 */
final class J2clMojoBuildMavenContext extends J2clMavenContext {

    static J2clMojoBuildMavenContext with(final J2clPath cache,
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
                                          final int threadPoolSize,
                                          final MavenLogger logger) {
        return new J2clMojoBuildMavenContext(
                cache,
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
                threadPoolSize,
                logger
        );
    }

    private J2clMojoBuildMavenContext(final J2clPath cache,
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
                                      final int threadPoolSize,
                                      final MavenLogger logger) {
        super(
                cache,
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
                threadPoolSize,
                logger
        );
        this.entryPoints = entryPoints;
        this.initialScriptFilename = initialScriptFilename;
    }

    @Override
    public J2clSourcesKind sourcesKind() {
        return J2clSourcesKind.SRC;
    }

    @Override
    public List<String> entryPoints() {
        return this.entryPoints;
    }

    private final List<String> entryPoints;

    @Override
    public J2clPath initialScriptFilename(final J2clArtifact artifact) {
        return this.initialScriptFilename;
    }

    private final J2clPath initialScriptFilename;

    @Override
    public void computeHash(final J2clArtifact artifact,
                            final HashBuilder hash,
                            final Set<String> hashItemNames) {
        if (false == artifact.isDependency()) {
            this.computeEntryPointAndInitialScriptFilenameHash(
                    this.entryPoints,
                    this.initialScriptFilename,
                    hashItemNames,
                    hash
            );
        }
    }

    @Override
    public J2clPath compiledBinaries(final J2clArtifact artifact) {
        return this.compiledBinariesTaskDirectory(artifact);
    }

    // tasks....................................................................................................

    @Override
    List<J2clTaskKind> tasks(final J2clArtifact artifact) {
        return artifact.isDependency() ?
                DEPENDENCY_TASKS :
                PROJECT_TASKS;
    }

    private final List<J2clTaskKind> DEPENDENCY_TASKS = Lists.of(
            J2clTaskKind.HASH,
            J2clTaskKind.UNPACK,
            J2clTaskKind.JAVAC_COMPILE,
            J2clTaskKind.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE,
            J2clTaskKind.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE,
            J2clTaskKind.SHADE_JAVA_SOURCE,
            J2clTaskKind.SHADE_CLASS_FILES,
            J2clTaskKind.TRANSPILE_JAVA_TO_JAVASCRIPT
    );

    private final List<J2clTaskKind> PROJECT_TASKS = Lists.of(
            J2clTaskKind.HASH,
            J2clTaskKind.JAVAC_COMPILE,
            J2clTaskKind.GWT_INCOMPATIBLE_STRIP_JAVA_SOURCE,
            J2clTaskKind.JAVAC_COMPILE_GWT_INCOMPATIBLE_STRIPPED_JAVA_SOURCE,
            J2clTaskKind.SHADE_JAVA_SOURCE,
            J2clTaskKind.SHADE_CLASS_FILES,
            J2clTaskKind.TRANSPILE_JAVA_TO_JAVASCRIPT,
            J2clTaskKind.CLOSURE_COMPILE,
            J2clTaskKind.OUTPUT_ASSEMBLE
    );

    @Override
    public boolean shouldCheckCache() {
        return true;
    }

    // J2clMavenContext.................................................................................................

    @Override
    boolean shouldSkipSubmittingDependencyTasks() {
        return false;
    }
}
