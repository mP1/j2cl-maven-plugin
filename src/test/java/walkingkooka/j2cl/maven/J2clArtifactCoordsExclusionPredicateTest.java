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

import org.junit.jupiter.api.Test;
import walkingkooka.ToStringTesting;
import walkingkooka.predicate.PredicateTesting2;

public final class J2clArtifactCoordsExclusionPredicateTest implements PredicateTesting2<J2clArtifactCoordsExclusionPredicate, J2clArtifactCoords>,
        ToStringTesting<J2clArtifactCoordsExclusionPredicate> {

    private final static String GROUP = "group1";
    private final static String ARTIFACT = "artifact2";
    private final static String VERSION = "version3";

    @Test
    public void testDifferentGroupId() {
        this.testFalse(J2clArtifactCoords.parse("different:" + ARTIFACT + ":" + VERSION));
    }

    @Test
    public void testDifferentArtifactId() {
        this.testFalse(J2clArtifactCoords.parse(GROUP + ":different:" + VERSION));
    }

    @Test
    public void testDifferentVersionId() {
        this.testTrue(J2clArtifactCoords.parse(GROUP + ":" + ARTIFACT + ":" + VERSION));
    }

    @Test
    public void testToString() {
        this.toStringAndCheck(this.createPredicate(), GROUP + ":" + ARTIFACT);
    }

    @Override
    public J2clArtifactCoordsExclusionPredicate createPredicate() {
        return J2clArtifactCoordsExclusionPredicate.with(GROUP, ARTIFACT);
    }

    @Override
    public Class<J2clArtifactCoordsExclusionPredicate> type() {
        return J2clArtifactCoordsExclusionPredicate.class;
    }
}
