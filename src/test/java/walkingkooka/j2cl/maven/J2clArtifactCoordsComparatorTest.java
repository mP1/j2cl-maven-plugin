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

import org.junit.Test;
import walkingkooka.Cast;
import walkingkooka.ToStringTesting;
import walkingkooka.compare.ComparatorTesting2;

import java.util.Comparator;

public final class J2clArtifactCoordsComparatorTest implements ComparatorTesting2<Comparator<J2clArtifactCoords>, J2clArtifactCoords>,
        ToStringTesting<Comparator<J2clArtifactCoords>> {

    @Test
    public void testDifferentGroupId() {
        this.compareAndCheckLess(J2clArtifactCoords.parse("group1:artifact2:version2"), J2clArtifactCoords.parse("group99:artifact2:version3"));
    }

    @Test
    public void testDifferentArtifactId() {
        this.compareAndCheckLess(J2clArtifactCoords.parse("group1:artifact2:version2"), J2clArtifactCoords.parse("group1:artifact99:version3"));
    }

    @Test
    public void testDifferentVersion() {
        this.compareAndCheckEquals(J2clArtifactCoords.parse("group1:artifact2:version3"), J2clArtifactCoords.parse("group1:artifact2:version99"));
    }

    @Test
    public void testDifferentType() {
        this.compareAndCheckEquals(J2clArtifactCoords.parse("group1:artifact2:type3:classifier4:version5"), J2clArtifactCoords.parse("group1:artifact2:type9:classifier4:version5"));
    }

    @Test
    public void testDifferentClassifier() {
        this.compareAndCheckLess(J2clArtifactCoords.parse("group1:artifact2:type3:classifier4:version5"), J2clArtifactCoords.parse("group1:artifact2:type3:classifier9:version5"));
    }

    @Test
    public void testToString() {
        this.toStringAndCheck(this.createComparator(), "groupId artifactId classifier");
    }

    @Override
    public Comparator<J2clArtifactCoords> createComparator() {
        return J2clArtifactCoordsComparator.INSTANCE;
    }

    @Override
    public Class<Comparator<J2clArtifactCoords>> type() {
        return Cast.to(J2clArtifactCoordsComparator.class);
    }
}
