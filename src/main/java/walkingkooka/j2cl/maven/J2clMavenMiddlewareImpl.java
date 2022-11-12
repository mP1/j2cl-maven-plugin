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
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import walkingkooka.collect.map.Maps;
import walkingkooka.text.CharSequences;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class J2clMavenMiddlewareImpl implements J2clMavenMiddleware {

    static J2clMavenMiddleware with(final ArtifactHandlerManager artifactHandlerManager,
                                    final MavenSession mavenSession,
                                    final ProjectBuilder projectBuilder,
                                    final List<ArtifactRepository> remoteArtifactRepositories,
                                    final List<RemoteRepository> remoteRepositories,
                                    final RepositorySystemSession repositorySession,
                                    final RepositorySystem repositorySystem) {
        return new J2clMavenMiddlewareImpl(artifactHandlerManager,
                mavenSession,
                projectBuilder,
                remoteArtifactRepositories,
                remoteRepositories,
                repositorySession,
                repositorySystem);
    }

    private J2clMavenMiddlewareImpl(final ArtifactHandlerManager artifactHandlerManager,
                                    final MavenSession mavenSession,
                                    final ProjectBuilder projectBuilder,
                                    final List<ArtifactRepository> remoteArtifactRepositories,
                                    final List<RemoteRepository> remoteRepositories,
                                    final RepositorySystemSession repositorySession,
                                    final RepositorySystem repositorySystem) {
        this.artifactHandlerManager = artifactHandlerManager;
        this.mavenSession = mavenSession;
        this.projectBuilder = projectBuilder;
        this.remoteArtifactRepositories = remoteArtifactRepositories;
        this.remoteRepositories = remoteRepositories;
        this.repositorySession = repositorySession;
        this.repositorySystem = repositorySystem;
    }

    /**
     * Fetches the {@link ArtifactHandler} for the given type.
     */
    @Override
    public MavenProject mavenProject(final J2clArtifactCoords coords,
                                     final J2clClasspathScope scope) {
        MavenProject mavenProject = this.coordToMavenProject.get(coords);
        if (null == mavenProject) {
            mavenProject = this.mavenProject0(coords.mavenArtifact(scope, this.artifactHandler(coords.typeOrDefault())));
            this.coordToMavenProject.put(coords, mavenProject);
        }

        return mavenProject;
    }

    /**
     * Cache of coords to project.
     */
    private final Map<J2clArtifactCoords, MavenProject> coordToMavenProject = Maps.concurrent();

    private ArtifactHandler artifactHandler(final String type) {
        return this.artifactHandlerManager.getArtifactHandler(type);
    }

    private final ArtifactHandlerManager artifactHandlerManager;

    private MavenProject mavenProject0(final Artifact artifact) {
        final ProjectBuildingRequest request = new DefaultProjectBuildingRequest(this.mavenSession.getProjectBuildingRequest());
        request.setProject(null);
        request.setResolveDependencies(true);
        request.setRemoteRepositories(this.remoteArtifactRepositories);

        try {
            return this.projectBuilder.build(artifact, false, request)
                    .getProject();

        } catch (final ProjectBuildingException cause) {
            throw new J2clException("Unable to fetch MavenProject for " + CharSequences.quoteAndEscape(artifact.toString()), cause);
        }
    }

    @Override
    public Optional<J2clPath> mavenFile(final String coords) {
        final ArtifactRequest request = new ArtifactRequest()
                .setRepositories(this.remoteRepositories)
                .setArtifact(new DefaultArtifact(coords));

        J2clPath file;
        try {
            file = J2clPath.with(this.repositorySystem.resolveArtifact(this.repositorySession, request).getArtifact().getFile().toPath());
        } catch (final ArtifactResolutionException cause) {
            file = null;
        }
        return Optional.ofNullable(file);
    }

    private final MavenSession mavenSession;
    private final ProjectBuilder projectBuilder;
    private final List<ArtifactRepository> remoteArtifactRepositories;
    private final List<RemoteRepository> remoteRepositories;
    private final RepositorySystemSession repositorySession;
    private final RepositorySystem repositorySystem;
}
