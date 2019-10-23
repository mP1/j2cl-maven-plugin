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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;

/**
 * Cleans by deleting the ENTIRE cache directory.
 */
@Mojo(name = "clean", aggregator = true)
public class J2clMojoClean extends J2clMojo {

    @Override
    public void execute() throws MojoFailureException {
        final J2clPath cache = this.cache();

        try {
            cache.removeAll();
        } catch (final IOException cause) {
            throw new MojoFailureException("Failed to clean " + cache, cause);
        }
    }
}
