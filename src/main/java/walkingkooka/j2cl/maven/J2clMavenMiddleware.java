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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;
import java.util.List;
import java.util.Optional;

public interface J2clMavenMiddleware {

    static J2clMavenMiddleware of(final MavenSession mavenSession,
                                  final RepositorySystem repositorySystem,
                                  final RepositorySystemSession repositorySession,
                                  final List<RemoteRepository> repositories,
                                  final ProjectBuilder projectBuilder,
                                  final MavenProject project) {
        return J2clMavenMiddlewareImpl.with(mavenSession, repositorySystem, repositorySession, repositories, projectBuilder, project);
    }

    /**
     * Returns a {@link MavenProject} given an {@link Artifact}.
     */
    MavenProject mavenProject(final Artifact artifact);

    Optional<File> mavenFile(final String coords);
}
