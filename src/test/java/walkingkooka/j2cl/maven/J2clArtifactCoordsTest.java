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

import org.apache.maven.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;
import walkingkooka.HashCodeEqualsDefinedTesting2;
import walkingkooka.ToStringTesting;
import walkingkooka.compare.ComparableTesting2;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;
import walkingkooka.text.CharSequences;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2clArtifactCoordsTest implements ClassTesting2<J2clArtifactCoords>,
        ComparableTesting2<J2clArtifactCoords>,
        HashCodeEqualsDefinedTesting2<J2clArtifactCoords>,
        ToStringTesting<J2clArtifactCoords> {

    private final static String GROUP = "group1";
    private final static String ARTIFACT = "artifact2";
    private final static String TYPE = "type3";
    private final static Optional<String> CLASSIFIER = Optional.of("classifier4");
    private final static String VERSION = "5";

    // withArtifact............................................................................................................

    @Test
    public void testWithArtifact() {
        assertEquals(this.createObject(), J2clArtifactCoords.with(new DefaultArtifact(GROUP, ARTIFACT, VERSION, "compile", TYPE, CLASSIFIER.get(), null)));
    }

    // parse............................................................................................................

    @Test
    public void testParseEmptyFails() {
        this.parseFails("");
    }

    @Test
    public void testParseGroupIdOnlyFails() {
        this.parseFails("group-id-only");
    }

    @Test
    public void testParseInvalidFails() {
        this.parseFails("group1:artifact2");
    }

    private void parseFails(final String text) {
        assertThrows(IllegalArgumentException.class, () ->
                J2clArtifactCoords.parse(text));
    }

    @Test
    public void testParseWithoutClassifier() {
        this.parseAndCheck("group1:artifact2:type3:5", J2clArtifactCoords.with("group1", "artifact2", "type3", Optional.empty(), "5"));
    }

    @Test
    public void testParseWithClassifier() {
        this.parseAndCheck("group1:artifact2:type3:classifier4:5", J2clArtifactCoords.with("group1", "artifact2", "type3", Optional.of("classifier4"), "5"));
    }

    private void parseAndCheck(final String text,
                               final J2clArtifactCoords expected) {
        assertEquals(expected,
                J2clArtifactCoords.parse(text),
                () -> "parse " + CharSequences.quoteAndEscape(text));
    }

    // comparable........................................................................................................

    @Test
    public void testGroupId() {
        this.checkNotEquals(J2clArtifactCoords.with("a", ARTIFACT, TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testLessArtifactId() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, "a", TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testLessVersion() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, "0"));
    }

    // equals...........................................................................................................

    @Test
    public void testDifferentGroupId() {
        this.checkNotEquals(J2clArtifactCoords.with("different", ARTIFACT, TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testDifferentArtifactId() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, "different", TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testDifferentType() {
        this.checkEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, "different", CLASSIFIER, VERSION));
    }

    @Test
    public void testDifferentClassifierAbsent() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.empty(), VERSION));
    }

    @Test
    public void testDifferentClassifier() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.ofNullable("different"), VERSION));
    }

    @Test
    public void testDifferentClassifierWildcardVersion() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.ofNullable("different"), "*"));
    }

    @Test
    public void testDifferentVersionWildcard() {
        this.checkEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, "*"));
    }

    @Test
    public void testDifferentVersion() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, "different"));
    }

    // sources...........................................................................................................

    @Test
    public void testSource() {
        assertEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.of("sources"), VERSION),
                J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, VERSION).source());
    }

    // ToString.........................................................................................................

    @Test
    public void testToString() {
        this.toStringAndCheck(this.createObject(), "group1:artifact2:type3:classifier4:5");
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<J2clArtifactCoords> type() {
        return J2clArtifactCoords.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PACKAGE_PRIVATE;
    }

    // ComparatorTesting2...............................................................................................

    @Override
    public J2clArtifactCoords createComparable() {
        return this.createObject();
    }

    // HashCodeEqualsDefinedTesting2....................................................................................

    @Override
    public J2clArtifactCoords createObject() {
        return J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, VERSION);
    }
}
