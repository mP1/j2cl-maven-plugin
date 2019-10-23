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

import org.apache.maven.project.MavenProject;

import java.util.List;

/**
 * Used to select either SRC or TEST sources.
 */
enum J2clSourcesKind {

    /**
     * Use the project and dependencies src directory.
     */
    SRC {
        @Override
        List<String> compileSourceRoots(final MavenProject project) {
            return project.getCompileSourceRoots();
        }
    },

    /**
     * When TEST use the project TEST directory.
     */
    TEST {
        @Override
        List<String> compileSourceRoots(final MavenProject project) {
            return project.getTestCompileSourceRoots();
        }
    };

    abstract List<String> compileSourceRoots(final MavenProject project);
}
