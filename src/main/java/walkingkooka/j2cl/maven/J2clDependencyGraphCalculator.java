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

/**
 * A calculator that creates a {@link Map} where each artifact is mapped to all its descendants including the addition of the requireds.
 */
final class J2clDependencyGraphCalculator {

    static J2clDependencyGraphCalculator with(final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat,
                                              final Set<J2clArtifactCoords> required) {
        return new J2clDependencyGraphCalculator(flat, required);
    }

    private J2clDependencyGraphCalculator(final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> flat,
                                          final Set<J2clArtifactCoords> required) {
        super();
        this.flat = Maps.sorted();
        flat.forEach((k, v) -> {
            final Set<J2clArtifactCoords> values = Sets.sorted();
            values.addAll(v);
            this.flat.put(k, values);
        });
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
            Set<J2clArtifactCoords> children = Sets.sorted();

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
                final Set<J2clArtifactCoords> collected = Sets.sorted();
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

    private void addTransitives() {
        this.tree.forEach((k, v) -> {
            final Set<J2clArtifactCoords> descendants = Sets.sorted();
            descendants.addAll(v);
            this.addTransitives0(this.tree.getOrDefault(k, Sets.empty()), descendants);
            this.tree.put(k, descendants);
        });
    }

    private void addTransitives0(final Set<J2clArtifactCoords> children,
                                 final Set<J2clArtifactCoords> descendants) {
        for(final J2clArtifactCoords child : children) {
            //if(descendants.add(child)) {
            descendants.add(child);
                this.addTransitives0(this.tree.getOrDefault(child, Sets.empty()), descendants);
            //}
        }
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
    private final Set<J2clArtifactCoords> leaves = Sets.sorted();


    /**
     * Coords to all dependencies including transitives and requireds.
     */
    private final Map<J2clArtifactCoords, Set<J2clArtifactCoords>> tree = Maps.sorted();

    @Override
    public String toString() {
        return this.tree.toString();
    }
}
