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

import walkingkooka.text.CharSequences;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@see CommandLineRunner} for available options, unfortunately the FormattingOption class is not public.
 */
enum ClosureFormattingOption {
    PRETTY_PRINT,
    PRINT_INPUT_DELIMITER,
    SINGLE_QUOTES;

    static ClosureFormattingOption fromCommandLine(final String option) {
        return Arrays.stream(ClosureFormattingOption.values())
                .filter(e -> e.name().equals(option) || e.name().toLowerCase().equals(option))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown formatting option " + CharSequences.quote(option) + " expected one of " +
                        Arrays.stream(ClosureFormattingOption.values()).map(Enum::name).collect(Collectors.joining(", ")) +
                        "\nhttps:///googleclosure.blogspot.com/2010/10/pretty-print-javascript-with-closure.html"));
    }
}
