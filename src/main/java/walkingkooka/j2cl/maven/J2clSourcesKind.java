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
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Used to select either SRC or TEST sources including resources
 */
enum J2clSourcesKind {

    /**
     * Use the project and dependencies src and resource directories.
     */
    SRC {
        @Override
        List<J2clPath> compileSourceRoots(final MavenProject project) {
            return concat(
                    sources(project.getCompileSourceRoots()),
                    resources(project.getResources())
            );
        }
    },

    /**
     * When TEST use the project SRC and TEST source and resource directories.
     */
    TEST {
        @Override
        List<J2clPath> compileSourceRoots(final MavenProject project) {
            return concat(
                    sources(project.getCompileSourceRoots(), project.getTestCompileSourceRoots()),
                    resources(project.getResources(), project.getTestResources())
            );
        }
    };

    abstract List<J2clPath> compileSourceRoots(final MavenProject project);

    static List<J2clPath> sources(final List<String>... sources) {
        return concat(Function.identity(), sources);
    }

    static List<J2clPath> resources(final List<Resource>... sources) {
        return concat(Resource::getDirectory, sources);
    }

    private static <T> List<J2clPath> concat(final Function<T, String> mapper,
                                             final List<T>... sources) {
        return Arrays.stream(sources)
                .filter(i -> null != i)
                .flatMap(s -> s.stream())
                .map(mapper)
                .map(Paths::get)
                .filter(Files::exists)
                .map(J2clPath::with)
                .distinct()
                .collect(Collectors.toList());
    }

    static List<J2clPath> concat(final List<J2clPath>... sources) {
        return Arrays.stream(sources)
                .flatMap(s -> s.stream())
                .collect(Collectors.toList());
    }
}
