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


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs all tests that are matched by `<tests>` as defined in the POM. The junit annotation processor will
 * create several files and {@link J2ClStepWorkerWebDriverUnitTestRunner} will prepare a HTML which will run all tests
 * and will be executed by webdriver.
 */
@Mojo(name = "test", requiresDependencyResolution = ResolutionScope.TEST)
public final class J2clMojoTest extends J2clMojoBuildTest {

    @Override
    public void execute() throws MojoExecutionException {
        if (false == this.skipTests()) {
            try {
                final J2clLogger logger = this.logger();
                this.executeTests(J2clLinePrinter.with(logger.printer(logger::debug)));
            } catch (final MojoExecutionException cause) {
                throw cause;
            } catch (final Exception cause) {
                throw new MojoExecutionException(cause.getMessage(), cause);
            }
        }
    }

    /**
     * Finds all test classes, strips, compiles, transpiles and closure compiles for each and every test.
     */
    private void executeTests(final J2clLinePrinter logger) throws Exception {
        final J2clMojoTestRequest request = this.request();
        final J2clDependency project = this.gatherDependencies(request);
        project.prettyPrintDependencies();
        request.verifyClasspathRequiredAndJavascriptSourceRequired();

        final List<String> tests = this.findTestClassNames(logger);

        logger.printLine("Tests");
        logger.indent();
        {
            for (final String test : tests) {
                logger.printLine(test);
                logger.indent();
                {
                    // re=use
                    try {
                        request.setTest(project, test);
                        request.execute(project);
                    } catch (final Throwable cause) {
                        throw new MojoExecutionException("Failed to build project, check logs above", cause);
                    }
                }
                logger.outdent();
            }
        }
        logger.outdent();
    }

    /**
     * The {@link J2clRequest} accompanying the build.
     */
    final J2clMojoTestRequest request() {
        return J2clMojoTestRequest.with(this.cache(),
                this.output(),
                this.classpathScope(),
                this.addedDependencies(),
                this.classpathRequired(),
                this.excludedDependencies(),
                this.javascriptSourceRequired(),
                this.processingSkipped(),
                this.replacedDependencies(),
                this.compilationLevel(),
                this.defines(),
                this.externs(),
                this.formatting(),
                this.languageOut(),
                this.mavenMiddleware(),
                this.executor(),
                this.logger());
    }

    /**
     * Loops over all test compile source roots, using <tests> patterns to match files returning the class names.
     */
    private List<String> findTestClassNames(final J2clLinePrinter logger) throws IOException {
        final List<String> allTests = Lists.array();

        logger.print("Test sources");
        logger.indent();
        {
            for (final String testSourceRoot : this.project().getTestCompileSourceRoots()) {
                final Path testSourceRootPath = Paths.get(testSourceRoot);

                logger.printLine(testSourceRootPath.toAbsolutePath().toString());
                logger.indent();
                {
                    final Set<String> tests = Sets.sorted();
                    final List<PathMatcher> pathMatchers = this.tests(testSourceRootPath);

                    Files.walkFileTree(testSourceRootPath, new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult visitFile(final Path file,
                                                         final BasicFileAttributes attributes) {
                            if (pathMatchers.stream().anyMatch(m -> m.matches(file))) {
                                tests.add(CharSequences.subSequence(testSourceRootPath.relativize(file).toString(), 0, -".java".length()).toString().replace(File.separatorChar, '.'));
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });
                    tests.forEach(logger::printLine);

                    allTests.addAll(tests);
                }
                logger.outdent();
            }
            logger.outdent();
        }

        return allTests;
    }

    // skipTests........................................................................................................

    @Parameter(defaultValue = "false", property = "maven.test.skip")
    private boolean skipTests;

    private boolean skipTests() {
        return this.skipTests;
    }

    // tests............................................................................................................

    /**
     * Zero or more individual test suite names. If this is empty the includes and excludes pattern will be used to discover tests.
     */
    @Parameter(alias = "tests", required = false)
    private List<String> tests;

    /**
     * Creates a {@link PathMatcher} from the given glob test patterns.
     */
    private List<PathMatcher> tests(final Path base) {
        return this.tests.stream()
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + base + File.separator + p.trim().replace('.', File.separatorChar) + ".java"))
                .collect(Collectors.toList());
    }
}