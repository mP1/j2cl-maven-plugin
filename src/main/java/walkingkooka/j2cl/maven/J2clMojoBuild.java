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


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Builds the given project and all of its dependencies in the correct order producing a single JS file.
 */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public final class J2clMojoBuild extends J2clMojoBuildTest {

    @Override
    public void execute() throws MojoExecutionException {
        try {
            final J2clRequest request = this.request(this.entryPoints(),
                    this.initialScriptFilename());
            request.execute(this.gatherDependencies(request));
        } catch (final Throwable e) {
            throw new MojoExecutionException("Failed to build project, check logs above", e);
        }
    }

    @Override
    J2clSourcesKind sourcesKind() {
        return J2clSourcesKind.SRC;
    }

    // entry-points.....................................................................................................

    private List<String> entryPoints() {
        return this.entrypoints.stream()
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @Parameter(alias = "entry-points", required = true)
    private List<String> entrypoints = new ArrayList<>();

    // initial-script-filename..........................................................................................

    private J2clPath initialScriptFilename() {
        return J2clPath.with(Paths.get(this.initialScriptFilename));
    }

    @Parameter(alias = "initial-script-filename",
            defaultValue = "${project.groupId}-${project.artifactId}.js",
            required = true)
    private String initialScriptFilename;
}
