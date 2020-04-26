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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import walkingkooka.text.CharSequences;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <pre>
 *     String SCOPE_COMPILE = "compile";
 *     String SCOPE_COMPILE_PLUS_RUNTIME = "compile+runtime";
 *     String SCOPE_TEST = "test";
 *     String SCOPE_RUNTIME = "runtime";
 *     String SCOPE_RUNTIME_PLUS_SYSTEM = "runtime+system";
 *     String SCOPE_PROVIDED = "provided";
 *     String SCOPE_SYSTEM = "system";
 *     String SCOPE_IMPORT = "import";
 * </pre>
 */
enum J2clClasspathScope {

    COMPILE(Artifact.SCOPE_COMPILE),
    COMPILE_PLUS_RUNTIME(Artifact.SCOPE_COMPILE_PLUS_RUNTIME),
    TEST(Artifact.SCOPE_TEST),
    RUNTIME(Artifact.SCOPE_RUNTIME),
    RUNTIME_PLUS_SYSTEM(Artifact.SCOPE_RUNTIME_PLUS_SYSTEM),
    PROVIDED(Artifact.SCOPE_PROVIDED),
    SYSTEM(Artifact.SCOPE_SYSTEM),
    IMPORT(Artifact.SCOPE_IMPORT);

    J2clClasspathScope(final String scope) {
        this.scope = scope;
        this.filter = new ScopeArtifactFilter(scope)::include;
    }

    /**
     * Only the scope is important and used by the filter.
     * scope=provided is handled as scope=compile/runtime so j2cl-JRE dependencies are ignored by JRE at runtime, but
     * used as a COMPILE/RUNTIME time dependency during this build.
     */
    Predicate<String> scopeFilter() {
        return (s) -> s.equalsIgnoreCase("provided") ||
                this.filter.test(new DefaultArtifact("groupId", "artifactId", "version", s, "type", "class", null));
    }

    private final Predicate<Artifact> filter;

    final String scope;

    static J2clClasspathScope commandLineOption(final String text) {
        return Arrays.stream(values())
                .filter(s -> s.scope.equals(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown option " +
                        CharSequences.quote(text) +
                        " expected one of " +
                        Arrays.stream(values()).map(s -> s.scope).collect(Collectors.joining(", "))));
    }
}

