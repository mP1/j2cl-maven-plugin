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
import org.apache.maven.artifact.handler.ArtifactHandler;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maven coordinates that identifies a single artifact.
 */
final class J2clArtifactCoords implements Comparable<J2clArtifactCoords> {

    static J2clArtifactCoords parse(final String coords) {
        final org.eclipse.aether.artifact.DefaultArtifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(coords);
        final String classifier = artifact.getClassifier();

        return new J2clArtifactCoords(artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getExtension(),
                Optional.ofNullable(classifier.isEmpty() ? null : classifier),
                artifact.getVersion(),
                artifact.getVersion());
    }

    static J2clArtifactCoords with(final Artifact artifact) {
        return with(artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getType(),
                Optional.ofNullable(artifact.getClassifier()),
                artifact.getVersion(),
                artifact.getBaseVersion());
    }

    private static J2clArtifactCoords with(final String groupId,
                                           final String artifactId,
                                           final String type,
                                           final Optional<String> classifier,
                                           final String version,
                                           final String baseVersion) {
        return new J2clArtifactCoords(groupId, artifactId, type, classifier, version, baseVersion);
    }

    private J2clArtifactCoords(final String groupId,
                               final String artifactId,
                               final String type,
                               final Optional<String> classifier,
                               final String version,
                               final String baseVersion) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.classifier = classifier;
        this.version = version;

        this.baseVersion = baseVersion;
    }

    /**
     * Used to form source coordinates so {@link J2ClBuildStepWorkerUnpack} can fetch the accompany sources.jar for any dependency.
     */
    J2clArtifactCoords source() {
        return new J2clArtifactCoords(this.groupId,
                this.artifactId,
                this.type,
                Optional.of("sources"),
                this.baseVersion,
                this.baseVersion);
    }

    String baseVersion() {
        return this.baseVersion;
    }

    /**
     * This is kept so the original coordinates are available allowing #source to work.
     */
    private final String baseVersion;

    /**
     * Creates a {@link Artifact}
     */
    Artifact mavenArtifact(final J2clClasspathScope scope,
                           final ArtifactHandler handler) {
        return new DefaultArtifact(this.groupId,
                this.artifactId,
                this.version,
                scope.scope,
                this.type,
                this.classifier.orElse(null),
                handler);
    }

    // Object...........................................................................................................

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.type, this.classifier, this.version);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof J2clArtifactCoords && this.compareTo((J2clArtifactCoords) other) == 0);
    }

    @Override
    public int compareTo(final J2clArtifactCoords other) {
        int result = this.groupId.compareTo(other.groupId);
        if (0 == result) {
            result = this.artifactId.compareTo(other.artifactId);
            if (0 == result) {
                result = this.baseVersion.compareTo(other.baseVersion);
                if (0 == result) {
                    result = this.type.compareTo(other.type);
                    if (0 == result) {
                        result = this.compareToClassifier().compareTo(other.compareToClassifier());
                    }
                }
            }
        }
        return result;
    }

    private String compareToClassifier() {
        return this.classifier.orElse("");
    }

    String groupId() {
        return this.groupId;
    }

    private final String groupId;

    String artifactId() {
        return this.artifactId;
    }

    private final String artifactId;

    String type() {
        return this.type;
    }

    private final String type;

    Optional<String> classifier() {
        return this.classifier;
    }

    private final Optional<String> classifier;

    String version() {
        return this.version;
    }

    private final String version;

    /**
     * Returns these coordinates as a safe directory name. This means replacing colons with dash dash, and
     * separating components with a dash instead of a colon.
     */
    String directorySafeName() {
        return this.buildString(this::safeEncoder, '-');
    }

    private String safeEncoder(final String name) {
        return name.replace(":", "--");
    }

    @Override
    public String toString() {
        return this.buildString(Function.identity(), ':');
    }

    private String buildString(final Function<String, String> encoder,
                               final char separator) {
        final StringBuilder b = new StringBuilder();
        b.append(encoder.apply(this.groupId));
        b.append(separator);
        b.append(encoder.apply(this.artifactId));
        b.append(separator);
        b.append(encoder.apply(this.type));
        this.classifier.ifPresent(c -> {
            b.append(separator);
            b.append(encoder.apply(c));
        });

        b.append(separator);
        b.append(encoder.apply(this.version));

        return b.toString();
    }
}
