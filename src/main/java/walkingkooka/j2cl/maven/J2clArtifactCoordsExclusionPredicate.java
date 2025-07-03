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

import java.util.function.Predicate;

final class J2clArtifactCoordsExclusionPredicate implements Predicate<J2clArtifactCoords> {

    static J2clArtifactCoordsExclusionPredicate with(final String groupId, final String artifactId) {
        return new J2clArtifactCoordsExclusionPredicate(groupId, artifactId);
    }

    private J2clArtifactCoordsExclusionPredicate(final String groupId, final String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public boolean test(final J2clArtifactCoords coords) {
        return null != coords &&
                this.groupId.equals(coords.groupId()) &&
                this.artifactId.equals(coords.artifactId());
    }

    private final String groupId;
    private final String artifactId;

    @Override
    public String toString() {
        return this.groupId + ":" + this.artifactId;
    }
}
