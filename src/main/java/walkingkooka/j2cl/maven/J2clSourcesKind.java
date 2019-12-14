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


import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to select either SRC or TEST sources including resources
 */
enum J2clSourcesKind {

    /**
     * Use the project and dependencies src directory.
     */
    SRC {
        @Override
        List<J2clPath> compileSourceRoots(final MavenProject project) {
            return this.compileSourceRoots0(project.getCompileSourceRoots(), project.getResources());
        }
    },

    /**
     * When TEST use the project TEST directory.
     */
    TEST {
        @Override
        List<J2clPath> compileSourceRoots(final MavenProject project) {
            return this.compileSourceRoots0(project.getTestCompileSourceRoots(), project.getTestResources());
        }
    };

    abstract List<J2clPath> compileSourceRoots(final MavenProject project);

    final List<J2clPath> compileSourceRoots0(final List<String> sources,
                                             final List<Resource> resources) {
        return Stream.concat(
                paths(sources, Function.identity()),
                paths(resources, Resource::getDirectory))
                .map(Paths::get)
                .filter(Files::exists)
                .map(J2clPath::with)
                .collect(Collectors.toList());
    }

    /**
     * Handles a source root and resources which may be null when missing into a {@link Stream} of {@link J2clPath paths}.
     */
    private static <S> Stream<String> paths(final List<S> sources,
                                            final Function<S, String> path) {
        return null != sources ?
                sources.stream().map(path) :
                Stream.empty();
    }
}
