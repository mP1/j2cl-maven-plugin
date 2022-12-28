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

import walkingkooka.collect.list.Lists;

import java.util.AbstractList;
import java.util.List;

/**
 * A {@link List} which ignores attempts to add an element a second time.
 * This is particularly useful when collecting a classpath.
 */
public final class UniqueList<T> extends AbstractList<T> {

    public static <T> UniqueList<T> empty() {
        return new UniqueList<>();
    }

    private UniqueList() {
        super();
    }

    @Override
    public boolean add(final T t) {
        boolean added = false;

        if (!this.list.contains(t)) {
            this.list.add(t);
            added = true;
        }

        return added;
    }

    @Override
    public T get(final int index) {
        return this.list.get(index);
    }

    @Override
    public int size() {
        return this.list.size();
    }

    private final List<T> list = Lists.array();
}
