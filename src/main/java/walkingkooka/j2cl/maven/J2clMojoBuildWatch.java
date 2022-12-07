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


import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

abstract class J2clMojoBuildWatch extends J2clMojoBuildTestWatch {

    // entry-points.....................................................................................................

    final List<String> entryPoints() {
        return this.entryPoints.stream()
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @Parameter(alias = "entry-points", required = true)
    private final List<String> entryPoints = new ArrayList<>();

    // initial-script-filename..........................................................................................

    final J2clPath initialScriptFilename() {
        return J2clPath.with(Paths.get(this.initialScriptFilename));
    }

    @Parameter(
            alias = "initial-script-filename",
            defaultValue = "${project.build.directory}/${project.build.finalName}/${project.groupId}-${project.artifactId}.js",
            required = true
    )
    private String initialScriptFilename;
}
