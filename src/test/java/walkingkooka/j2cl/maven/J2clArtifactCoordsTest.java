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

import static org.junit.jupiter.api.Assertions.assertSame;
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
        this.checkEquals(this.createObject(), J2clArtifactCoords.with(new DefaultArtifact(GROUP, ARTIFACT, VERSION, "compile", TYPE, CLASSIFIER.get(), null)));
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
        this.checkEquals(expected,
                J2clArtifactCoords.parse(text),
                () -> "parse " + CharSequences.quoteAndEscape(text));
    }

    // typeOrDefault.....................................................................................................

    @Test
    public void testTypeOrDefaultNull() {
        final J2clArtifactCoords coords = J2clArtifactCoords.with("group1",
                "artifact2",
                null,
                Optional.ofNullable("classifier3"),
                "version4");
        typeOrDefaultAndCheck(coords, "jar");
    }

    @Test
    public void testTypeOrDefaultEmpty() {
        final J2clArtifactCoords coords = J2clArtifactCoords.with("group1",
                "artifact2",
                "",
                Optional.ofNullable("classifier3"),
                "version4");
        typeOrDefaultAndCheck(coords, "jar");
    }

    @Test
    public void testTypeOrDefault() {
        this.typeOrDefaultAndCheck("group1:artifact2:type3:classifier4:version5", "type3");
    }

    private void typeOrDefaultAndCheck(final String parse, final String type) {
        this.typeOrDefaultAndCheck(J2clArtifactCoords.parse(parse), type);
    }

    private void typeOrDefaultAndCheck(final J2clArtifactCoords coords, final String type) {
        this.checkEquals(type, coords.typeOrDefault(), () -> coords + "  typeOrDefault");
    }

    // comparable........................................................................................................

    @Test
    public void testGroupId() {
        this.checkNotEquals(J2clArtifactCoords.with("a", ARTIFACT, TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testWildcardArtifactId() {
        this.checkEquals(J2clArtifactCoords.with(GROUP, "*", TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testLessArtifactId() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, "a", TYPE, CLASSIFIER, VERSION));
    }

    @Test
    public void testLessVersion() {
        this.checkNotEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, "0"));
    }

    @Test
    public void testWildcardVersion() {
        this.checkEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, "*"));
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

    // dependencyManagement ............................................................................................

    @Test
    public void testDependencyManagementDifferentGroupId() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:version3",
                "DIFFERENT:artifact2:version3");
    }

    @Test
    public void testDependencyManagementDifferentGroupId2() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:version3",
                "DIFFERENT:artifact2:99");
    }

    @Test
    public void testDependencyManagementDifferentArtifactId() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:version3",
                "group1:DIFFERENT:version3");
    }

    @Test
    public void testDependencyManagementDifferentArtifactId2() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:version3",
                "group1:DIFFERENT:99");
    }

    @Test
    public void testDependencyManagementDifferentVersion() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:version3",
                "group1:artifact2:DIFFERENT",
                "group1:artifact2:version3");
    }

    @Test
    public void testDependencyManagementDifferentVersion2() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:version3",
                "group1:artifact2:type3:classifier4:DIFFERENT-VERSION",
                "group1:artifact2:type3:classifier4:version3");
    }

    @Test
    public void testDependencyManagementDifferentVersion3() {
        this.dependencyManagementTransformerAndCheck("group1:artifact2:999",
                "group1:artifact2:type3:classifier4:DIFFERENT-VERSION",
                "group1:artifact2:type3:classifier4:999");
    }

    private void dependencyManagementTransformerAndCheck(final String dependencyManagement,
                                                         final String test) {
        this.dependencyManagementTransformerAndCheck(dependencyManagement, test, test);
    }

    private void dependencyManagementTransformerAndCheck(final String dependencyManagement,
                                                         final String test,
                                                         final String expected) {
        this.dependencyManagementTransformerAndCheck(J2clArtifactCoords.parse(dependencyManagement),
                J2clArtifactCoords.parse(test),
                J2clArtifactCoords.parse(expected));
    }

    private void dependencyManagementTransformerAndCheck(final J2clArtifactCoords dependencyManagement,
                                                         final J2clArtifactCoords coords,
                                                         final J2clArtifactCoords expected) {

        this.checkEquals(expected,
                dependencyManagement.dependencyManagementTransformer().apply(coords),
                () -> "dependenecyManagement" + dependencyManagement + " transform " + coords);
    }

    // sources...........................................................................................................

    @Test
    public void testSource() {
        final J2clArtifactCoords notSources = J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, CLASSIFIER, VERSION);
        final J2clArtifactCoords sources = notSources.source();

        this.checkEquals(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.of("sources"), VERSION),
                sources);
        this.isSourcesAndCheck(sources, true);
        this.isSourcesAndCheck(notSources, false);
    }

    @Test
    public void testSource2() {
        final J2clArtifactCoords sources = J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.of("sources"), VERSION);

        assertSame(sources, sources.source());

        this.isSourcesAndCheck(sources, true);
    }

    // isSources...........................................................................................................

    @Test
    public void testIsSourceAbsentFalse() {
        this.isSourcesAndCheck((String) null, false);
    }

    @Test
    public void testIsSourceFalse() {
        this.isSourcesAndCheck("abc", false);
    }

    @Test
    public void testIsSourceTrue() {
        this.isSourcesAndCheck("sources", true);
    }

    private void isSourcesAndCheck(final String sources, final boolean expected) {
        this.isSourcesAndCheck(J2clArtifactCoords.with(GROUP, ARTIFACT, TYPE, Optional.ofNullable(sources), VERSION),
                expected);
    }

    private void isSourcesAndCheck(final J2clArtifactCoords sources, final boolean expected) {
        this.checkEquals(expected,
                sources.isSources(),
                () -> sources + " .isSources");
    }

    // isSameGroupArtifactDifferentVersion..............................................................................

    @Test
    public void testIsSameGroupArtifactAndDifferentVersionDifferentGroup() {
        this.isSameGroupArtifactsAndDifferentVersionAndCheck("group1:artifact2:version3", "DIFFERENT:artifact2:version3", false);
    }

    @Test
    public void testIsSameGroupArtifactAndDifferentVersionDifferentArtifact() {
        this.isSameGroupArtifactsAndDifferentVersionAndCheck("group1:artifact2:version3", "group1:DIFFERENT:version3", false);
    }

    @Test
    public void testIsSameGroupArtifactAndDifferentVersionDifferentVersion() {
        this.isSameGroupArtifactsAndDifferentVersionAndCheck("group1:artifact2:version3", "group1:artifact2:DIFFERENT", true);
    }

    @Test
    public void testIsSameGroupArtifactAndDifferentVersionDifferentType() {
        this.isSameGroupArtifactsAndDifferentVersionAndCheck("group1:artifact2:type3:classifier4:version5", "group1:artifact2:DIFFERENT:classifier4:version5", false);
    }

    @Test
    public void testIsSameGroupArtifactAndDifferentVersionDifferentClassifier() {
        this.isSameGroupArtifactsAndDifferentVersionAndCheck("group1:artifact2:type3:classifier4:version5", "group1:artifact2:type3:DIFFERENT:version5", false);
    }

    private void isSameGroupArtifactsAndDifferentVersionAndCheck(final String coords,
                                                                 final String other,
                                                                 final boolean expected) {
        this.isSameGroupArtifactsAndDifferentVersionAndCheck(J2clArtifactCoords.parse(coords),
                J2clArtifactCoords.parse(other),
                expected);
    }

    private void isSameGroupArtifactsAndDifferentVersionAndCheck(final J2clArtifactCoords coords,
                                                                 final J2clArtifactCoords other,
                                                                 final boolean expected) {
        this.checkEquals(expected,
                coords.isSameGroupArtifactDifferentVersion(other),
                () -> coords + ".isSameGroupArtifactDifferentVersion " + other);
    }

    // isGroupArtifactSources........................................................................................

    @Test
    public void testIsGroupArtifactSourcesDifferentGroup() {
        this.isGroupArtifactSourcesAndCheck("group1:artifact2:type3:classifier4:5",
                "DIFFERENT:artifact2:type3:classifier4:5",
                false);
    }

    @Test
    public void testIsGroupArtifactSourcesDifferentArtifact() {
        this.isGroupArtifactSourcesAndCheck("group1:artifact2:type3:classifier4:5",
                "group1:DIFFERENT:type3:classifier4:5",
                false);
    }

    @Test
    public void testIsGroupArtifactSourcesSources() {
        this.isGroupArtifactSourcesAndCheck("group1:artifact2:type3:classifier4:5",
                "group1:artifact2:type3:sources:5",
                true);
    }

    private void isGroupArtifactSourcesAndCheck(final String coords,
                                                final String other,
                                                final boolean expected) {

        this.isGroupArtifactSourcesAndCheck(J2clArtifactCoords.parse(coords),
                J2clArtifactCoords.parse(other),
                expected);
    }

    private void isGroupArtifactSourcesAndCheck(final J2clArtifactCoords coords,
                                                final J2clArtifactCoords other,
                                                final boolean expected) {

        this.checkEquals(expected,
                coords.isGroupArtifactSources(other),
                () -> coords + " .isGroupArtifactSources " + other);

        this.checkEquals(expected,
                other.isGroupArtifactSources(coords),
                () -> other + " .isGroupArtifactSources " + coords);
    }

    // directorySafeName.................................................................................................

    @Test
    public void testDirectorySafeNameGroupArtifactVersion() {
        this.directorySafeNameAndCheck("group1:artifact2:version3", "group1--artifact2--jar--version3");
    }

    @Test
    public void testDirectorySafeNameGroupArtifactVersionWithDash() {
        this.directorySafeNameAndCheck("group-1:artifact-2:version-3", "group-1--artifact-2--jar--version-3");
    }

    @Test
    public void testDirectorySafeNameGroupArtifactClassifierVersion() {
        this.directorySafeNameAndCheck("group1:artifact2:type3:classifier4:version5", "group1--artifact2--type3--classifier4--version5");
    }

    private void directorySafeNameAndCheck(final String coords, final String directoryName) {
        this.checkEquals(directoryName, J2clArtifactCoords.parse(coords).directorySafeName(), () -> "Coords: " + CharSequences.quoteAndEscape(coords));
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
