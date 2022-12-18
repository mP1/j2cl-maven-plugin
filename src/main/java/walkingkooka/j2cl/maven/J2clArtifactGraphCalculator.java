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

import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A calculator that creates a {@link Map} where each artifact is mapped to all its descendants including the addition of the requireds.
 */
final class J2clArtifactGraphCalculator {

    static J2clArtifactGraphCalculator with(final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat,
                                            final Set<J2clArtifactCoords> required) {
        return new J2clArtifactGraphCalculator(flat, required);
    }

    private J2clArtifactGraphCalculator(final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat,
                                        final Set<J2clArtifactCoords> required) {
        super();
        this.flat = Maps.sorted();
        flat.forEach((k, v) -> {
            final Set<J2clArtifactCoords> values = J2clArtifactCoords.set();

            final String wildcard = v.stream()
                    .filter(J2clArtifactCoords::isWildcardVersion)
                    .map(J2clArtifactCoords::toString)
                    .collect(Collectors.joining(","));
            if (wildcard.length() > 0) {
                throw new IllegalArgumentException("Mapping of " + k + " may not contain wildcards=" + wildcard);
            }

            values.addAll(v);
            this.flat.put(k, values);
        });

        {
            final String wildcard = required.stream()
                    .filter(J2clArtifactCoords::isWildcardVersion)
                    .map(J2clArtifactCoords::toString)
                    .collect(Collectors.joining(","));
            if (wildcard.length() > 0) {
                throw new IllegalArgumentException("Required may not contain wildcards=" + wildcard);
            }
        }

        this.required = required;
    }

    Map<J2clArtifactCoords, Set<J2clArtifactCoords>> run() {
        this.fillLeavesAndTree();
        this.addRequiredToLeaves();
        this.addTransitives();
        return this.tree;
    }

    private void addRequiredToLeaves() {
        for(final J2clArtifactCoords leaf : this.leaves) {
            Set<J2clArtifactCoords> children = J2clArtifactCoords.set();

            for(final J2clArtifactCoords required : this.required) {
                if(required.equals(leaf)) {
                    children = Sets.empty();
                    break;
                }

                final Set<J2clArtifactCoords> graph = this.tree.getOrDefault(required, Sets.empty());
                if(false == graph.contains(leaf)) {
                    children.add(required);
                }
            }
            this.tree.put(leaf, children);
        }
    }

    private void fillLeavesAndTree() {
        for (final Entry<J2clArtifactCoords, Set<J2clArtifactCoords>> parentToChild : this.flat.entrySet()) {
            final J2clArtifactCoords parent = parentToChild.getKey();
            final Set<J2clArtifactCoords> children = parentToChild.getValue();
            children.removeAll(this.required);

            if (children.isEmpty()) {
                this.leaves.add(parent);
            } else {
                final Set<J2clArtifactCoords> collected = J2clArtifactCoords.set();
                this.collectTransitives(parent, children, collected);
                this.tree.put(parent, collected);
            }
        }
    }

    private void collectTransitives(final J2clArtifactCoords parent,
                                    final Set<J2clArtifactCoords> children,
                                    final Set<J2clArtifactCoords> collected) {
        for(final J2clArtifactCoords child : children) {
            if(false == parent.equals(child) && collected.add(child)) {
                this.collectTransitives(parent, this.flat.getOrDefault(child, Sets.empty()), collected);
            }
        }
    }

    /**
     * Keep repeating the processing of all entries in {@link #tree} each time another level of descendants should be added,
     * if none then it is complete and exit.
     */
    private void addTransitives() {
        boolean changes;

        do {
            changes = false;
            for (final Entry<J2clArtifactCoords, Set<J2clArtifactCoords>> parentToDependencies : this.tree.entrySet()) {
                final Set<J2clArtifactCoords> children = parentToDependencies.getValue();

                final Set<J2clArtifactCoords> descendants = J2clArtifactCoords.set();
                descendants.addAll(children);

                for (J2clArtifactCoords child : children) {
                    changes |= this.addTransitives0(child, descendants);
                }

                parentToDependencies.setValue(descendants);
            }
        } while (changes);
    }

    private boolean addTransitives0(final J2clArtifactCoords parent, final Set<J2clArtifactCoords> descendants) {
        boolean changes = false;

        for (final J2clArtifactCoords child : this.tree.getOrDefault(parent, Sets.empty())) {
            // if a new child also try and add its transitives...
            if (descendants.add(child)) {
                changes = true;
                this.addTransitives0(child, descendants);
            }
        }
        
        return changes;
    }

    /**
     * Coord to immediate flat.
     */
    private final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat;

    /**
     * Coords of artifacts that should be added to all dependencies.
     */
    private final Set<J2clArtifactCoords> required;


    /**
     * Coords to artifacts without any dependencies.
     */
    private final Set<J2clArtifactCoords> leaves = J2clArtifactCoords.set();


    /**
     * Coords to all dependencies including transitives and requireds.
     */
    private final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> tree = Maps.sorted();

    @Override
    public String toString() {
        return this.tree.toString();
    }
}
