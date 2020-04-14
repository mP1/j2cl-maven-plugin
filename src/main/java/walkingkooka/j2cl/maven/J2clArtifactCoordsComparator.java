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

import java.util.Comparator;

final class J2clArtifactCoordsComparator implements Comparator<J2clArtifactCoords> {

    /**
     * Singleton
     */
    final static J2clArtifactCoordsComparator INSTANCE = new J2clArtifactCoordsComparator();

    private J2clArtifactCoordsComparator() {
        super();
    }

    @Override
    public int compare(final J2clArtifactCoords l, final J2clArtifactCoords r) {
            int result = l.groupId().compareTo(r.groupId());
            if (0 == result) {
                result = l.artifactId().compareTo(r.artifactId());
                if (0 == result) {
                    result = l.compareToClassifier().compareTo(r.compareToClassifier());
                }
            }
            return result;
    }

    @Override
    public String toString() {
        return "groupId artifactId classifier";
    }
}
