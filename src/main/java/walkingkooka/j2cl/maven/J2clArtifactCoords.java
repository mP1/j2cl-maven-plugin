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
import org.apache.maven.model.Dependency;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Maven coordinates that identifies a single dependency or artifact.
 */
final class J2clArtifactCoords implements Comparable<J2clArtifactCoords> {

    /**
     * Creates a sorted {@link Set} to hold coords.
     */
    static Set<J2clArtifactCoords> set() {
        return Sets.sorted();
    }

    /**
     * A comparator that compares two coords ignoring the version.
     */
    static Comparator<J2clArtifactCoords> IGNORE_VERSION_COMPARATOR = J2clArtifactCoordsComparator.INSTANCE;

    static J2clArtifactCoords parse(final String coords) {
        final org.eclipse.aether.artifact.DefaultArtifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(coords);
        final String classifier = artifact.getClassifier();

        return new J2clArtifactCoords(artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getExtension(),
                Optional.ofNullable(classifier.isEmpty() ? null : classifier),
                artifact.getBaseVersion());
    }

    static J2clArtifactCoords with(final Dependency dependency) {
        return with(dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getType(),
                Optional.ofNullable(dependency.getClassifier()),
                dependency.getVersion());
    }

    static J2clArtifactCoords with(final Artifact artifact) {
        return with(artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getType(),
                Optional.ofNullable(artifact.getClassifier()),
                artifact.getBaseVersion());
    }

    // @VisibleForTesting
    static J2clArtifactCoords with(final String groupId,
                                   final String artifactId,
                                   final String type,
                                   final Optional<String> classifier,
                                   final String version) {
        return new J2clArtifactCoords(groupId, artifactId, type, classifier, version);
    }

    private J2clArtifactCoords(final String groupId,
                               final String artifactId,
                               final String type,
                               final Optional<String> classifier,
                               final String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.type = type;
        this.classifier = classifier;
        this.version = version;
    }

    /**
     * Used to form source coordinates so {@link J2clStepWorkerUnpack} can fetch the accompany sources.jar for any dependency.
     */
    J2clArtifactCoords source() {
        return this.isSources() ?
                this :
                new J2clArtifactCoords(this.groupId,
                        this.artifactId,
                        this.type,
                        Optional.of(SOURCES),
                        this.version);
    }

    /**
     * Returns true if this coordinate is a sources.
     */
    boolean isSources() {
        return this.classifier().stream().anyMatch(SOURCES::equalsIgnoreCase);
    }

    private final static String SOURCES = "sources";

    /**
     * Returns a {@link Function} which will be used to transform coords when they match an individual dependencyManagement POM entry.
     */
    Function<J2clArtifactCoords, J2clArtifactCoords> dependencyManagementTransformer() {
        return this::dependencyManagementTransformer;
    }

    private J2clArtifactCoords dependencyManagementTransformer(final J2clArtifactCoords coords) {
        return this.isSameGroupArtifactDifferentVersion(coords) ?
                new J2clArtifactCoords(coords.groupId, coords.artifactId, coords.type, coords.classifier, this.version) :
                coords;
    }

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

    /**
     * Returns true if the group, artifact are the same but the version is different. Note that wildcards are not honoured.
     */
    boolean isSameGroupArtifactDifferentVersion(final J2clArtifactCoords other) {
        return this.groupId().equals(other.groupId()) &&
                this.artifactId().equals(other.artifactId()) &&
                false == this.version().equals(other.version());
    }

    /**
     * Matches a coordinate if they share the same group and artifact but classifier != sources.
     */
    boolean isGroupArtifactSources(final J2clArtifactCoords other) {
        return this.groupId().equals(other.groupId()) &&
                this.artifactId().equals(other.artifactId()) &&
                this.isSources() != other.isSources();
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
                result = this.isWildcardVersion() || other.isWildcardVersion() ?
                        0 :
                        this.version.compareTo(other.version);
                if (0 == result) {
                    result = this.compareToClassifier().compareTo(other.compareToClassifier());
                }
            }
        }
        return result;
    }

    String compareToClassifier() {
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

    String typeOrDefault() {
        final String type = this.type();
        return CharSequences.isNullOrEmpty(type) ?
                "jar" :
                type;
    }

    private final String type;

    Optional<String> classifier() {
        return this.classifier;
    }

    private final Optional<String> classifier;

    String version() {
        return this.version;
    }

    boolean isWildcardVersion() {
        return this.version().equals("*");
    }

    private final String version;

    /**
     * Returns these coordinates as a safe directory name. This means replacing colons with dash dash, and
     * separating components with a dash instead of a colon.
     */
    String directorySafeName() {
        return this.toString()
                .replace(":", "--");
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        b.append(this.groupId);
        b.append(SEPARATOR);
        b.append(this.artifactId);
        b.append(SEPARATOR);
        b.append(this.type);
        this.classifier.ifPresent(c -> {
            b.append(SEPARATOR);
            b.append(c);
        });

        b.append(SEPARATOR);
        b.append(this.version);

        return b.toString();
    }

    private final static char SEPARATOR = ':';
}
