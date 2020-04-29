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

import walkingkooka.naming.StringPath;
import walkingkooka.text.printer.IndentingPrinter;

import java.util.Collection;
import java.util.function.Function;

// maven-plugin-plugin fails build because of enum method with generics, while scanning classpath for javadoc annotations.
enum J2clLinePrinterFormat {
    // Useful for printing a list of files where order is important such as a classpath.
    FLAT {
//        @Override
//        <T> void print(final Collection<T> paths,
//                       final Function<T, StringPath> toStringPath,
//                       final IndentingPrinter printer) {
//            J2clLinePrinter.printFlat(paths, toStringPath, printer);
//        }
    },

    // Useful for printing a tree of files which will appear lexicagraphically(???) sorted
    TREE {
//        @Override
//        <T> void print(final Collection<T> paths,
//                       final Function<T, StringPath> toStringPath,
//                       final IndentingPrinter printer) {
//            J2clLinePrinter.printTree(paths, toStringPath, printer);
//        }
    };

//    abstract <T> void print(final Collection<T> paths,
//                            final Function<T, StringPath> toStringPath,
//                            final IndentingPrinter printer);
}
