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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import walkingkooka.j2cl.maven.log.MavenLogger;

import java.io.File;

/**
 * Abstract base class for all maven goals in this plugin.
 */
abstract class J2clMojo extends AbstractMojo {

    J2clMojo() {
        super();
    }

    /**
     * Returns the path to the cache directory that contains all processed dependencies/artifacts.
     */
    final J2clPath cache() {
        return J2clPath.with(this.cache.toPath());
    }

    /**
     * Specifies the path fo the cache directory where all dependencies are processed and their respective files.
     */
    @Parameter(defaultValue = "${project.build.directory}/walkingkooka-j2cl-maven-plugin-cache", required = true, property = "walkingkooka-j2cl-maven-plugin.cache.dir")
    private File cache;

    // logging..........................................................................................................

    @Override
    public void setLog(final Log log) {
        super.setLog(log);
        this.logger = null;
    }

    /**
     * Returns a {@link MavenLogger} which wraps the real maven logger.
     */
    final MavenLogger logger() {
        if (null == this.logger) {
            this.logger = MavenLogger.maven(this.getLog());
        }
        return this.logger;
    }

    private MavenLogger logger;
}
