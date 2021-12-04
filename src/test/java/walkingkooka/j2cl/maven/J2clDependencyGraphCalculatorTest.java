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
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.reflect.ClassTesting2;
import walkingkooka.reflect.JavaVisibility;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class J2clDependencyGraphCalculatorTest implements ClassTesting2<J2clDependencyGraphCalculator> {

    private final static String A1 = "group:a1:1.0";
    private final static String B2 = "group:b2:1.0";
    private final static String C3 = "group:c3:1.0";
    private final static String D4 = "group:d4:1.0";

    private final static String NONE = "";

    private final static String R1 = "group:r1:1.0";
    private final static String R2 = "group:r2:1.0";
    private final static String R3 = "group:r3:1.0";

    @Test
    public void testMappingWithWildcardFails() {
        final Map<String, String> flat = Maps.of(A1, "group1:artifact2:*");
        final String required = "group4:artifact5:version6";

        assertThrows(IllegalArgumentException.class, () -> {
            J2clDependencyGraphCalculator.with(transform(flat), transformCsv(required));
        });
    }

    @Test
    public void testRequiredWithWildcardFails() {
        final Map<String, String> flat = Maps.of(A1, "group1:artifact2:version3");
        final String required = "group4:artifact5:*";

        assertThrows(IllegalArgumentException.class, () -> {
            J2clDependencyGraphCalculator.with(transform(flat), transformCsv(required));
        });
    }

    @Test
    public void testWithoutDependency() {
        final Map<String, String> flat = Maps.of(A1, NONE, R1, NONE);
        final String required = R1;
        final Map<String, String> tree = Maps.of(A1, R1, R1, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithoutDependencyMultipleRequires() {
        final Map<String, String> flat = Maps.of(A1, NONE, R1, NONE, R2, NONE);
        final String required = csv(R1, R2);
        final Map<String, String> tree = Maps.of(A1, required, R1, NONE, R2, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithRequired() {
        final Map<String, String> flat = Maps.of(A1, R1, R1, NONE);
        final String required = R1;
        final Map<String, String> tree = Maps.of(A1, R1, R1, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithRequired2() {
        final Map<String, String> flat = Maps.of(A1, csv(R1, R2), R1, NONE, R2, NONE);
        final String required = csv(R1, R2);
        final Map<String, String> tree = Maps.of(A1, csv(R1, R2), R1, NONE, R2, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithDependency() {
        final Map<String, String> flat = Maps.of(A1, B2, B2, NONE, R1, NONE);
        final String required = R1;
        final Map<String, String> tree = Maps.of(A1, csv(B2, R1), B2, R1, R1, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithDependencyMultipleRequireds() {
        final Map<String, String> flat = Maps.of(A1, B2, B2, NONE, R1, NONE, R2, NONE);
        final String required = csv(R1, R2);
        final Map<String, String> tree = Maps.of(A1, csv(B2, R1, R2), B2, csv(R1, R2), R1, NONE, R2, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithDependency2() {
        final Map<String, String> flat = Maps.of(A1, csv(B2, C3), B2, NONE, C3, NONE, R1, NONE);
        final String required = R1;
        final Map<String, String> tree = Maps.of(A1, csv(B2, C3, R1), B2, R1, C3, R1, R1, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithDependencyReferencesRequired() {
        final Map<String, String> flat = Maps.of(A1, B2, B2, R1, R1, NONE);
        final String required = R1;
        final Map<String, String> tree = Maps.of(A1, csv(B2, R1), B2, R1, R1, NONE);

        this.runAndCheck(flat, required, tree);
    }

    @Test
    public void testWithDependencyReferencesManyRequired() {
        final Map<String, String> flat = Maps.of(A1, B2, B2, R1, R1, NONE, R2, NONE);
        final String required = csv(R1, R2);
        final Map<String, String> tree = Maps.of(A1, csv(B2, R1, R2), B2, csv(R1, R2), R1, NONE, R2, NONE);

        this.runAndCheck(flat, required, tree);
    }

    private String csv(final String... strings) {
        return String.join(",", strings);
    }

    private void runAndCheck(final Map<String, String> flat,
                             final String required,
                             final Map<String, String> tree) {
        this.runAndCheck0(this.transform(flat),
                this.transformCsv(required),
                this.transform(tree));
    }

    private Map<J2clArtifactCoords, Set<J2clArtifactCoords>> transform(final Map<String, String> flat) {
        final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> transformed = Maps.sorted();

        flat.forEach((k, v) -> transformed.put(J2clArtifactCoords.parse(k), this.transformCsv(v)));

        return transformed;
    }

    private Set<J2clArtifactCoords> transformCsv(final String csv) {
        return Arrays.stream(csv.isEmpty() ? new String[0] : csv.split(","))
                .map(J2clArtifactCoords::parse)
                .collect(Collectors.toCollection(Sets::sorted));
    }

    private void runAndCheck0(final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat,
                              final Set<J2clArtifactCoords> required,
                              final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> tree) {
        assertNotEquals(Maps.empty(), flat, "flat is must not empty");
        assertNotEquals(Sets.empty(), required, "required is must not empty");

        final Set<J2clArtifactCoords> unknownRequireds = J2clArtifactCoords.set();
        unknownRequireds.addAll(required);
        unknownRequireds.removeAll(flat.keySet());
        this.checkEquals(Sets.empty(), unknownRequireds, "Required contains dependencies not found in flat: " + flat);

        this.checkEquals(flat.keySet(), tree.keySet(), "flat and tree keys must be the same");

        final J2clDependencyGraphCalculator calculator = J2clDependencyGraphCalculator.with(flat, required);
        this.checkEquals(format(tree),
                format(calculator.run()),
                () -> "flat: " + flat + " required: " + required);
    }

    private String format(final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> map) {
        return map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n"));
    }

    // ClassTesting.....................................................................................................

    @Override
    public Class<J2clDependencyGraphCalculator> type() {
        return J2clDependencyGraphCalculator.class;
    }

    @Override
    public JavaVisibility typeVisibility() {
        return JavaVisibility.PACKAGE_PRIVATE;
    }
}
