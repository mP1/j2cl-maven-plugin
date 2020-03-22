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

import com.google.common.io.CharStreams;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;

/**
 * Assumes that the closure compiler has completed successfully and then invokes web driver to execute the prepared
 * js file containing the tests.
 */
final class J2clStepWorkerWebDriverUnitTestRunner extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerWebDriverUnitTestRunner();
    }

    private J2clStepWorkerWebDriverUnitTestRunner() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        logger.printLine("Junit Tests");
        logger.indent();
        {
            final J2clRequest request = artifact.request();
            this.executeTestSuite(this.prepareJunitHostFileScriptPath(request, logger),
                    request.browsers(),
                    request.testTimeout(),
                    logger);
        }
        logger.outdent();

        return J2clStepResult.SUCCESS;
    }

    /**
     * Loads the html file and replaces the script file with the closure compiled test file.
     */
    private J2clPath prepareJunitHostFileScriptPath(final J2clRequest request,
                                                    final J2clLinePrinter logger) throws IOException {
        final J2clPath hostHtml;

        logger.printLine("Prepare host file");
        logger.indent();
        {
            final J2clPath file = request.initialScriptFilename();
            hostHtml = file.parent()
                    .append(CharSequences.subSequence(file.file().getName(), 0, -2) + "html");

            logger.printIndented("Compiled tests file", file);
            logger.printIndented("JUnit html host file", hostHtml);

            try (final InputStreamReader reader = new InputStreamReader(this.getClass().getResourceAsStream("junit.html"), DEFAULT_CHARSET)) {
                final String junitHtml = CharStreams.toString(reader)
                        .replace("<TEST_SCRIPT>", file.filename());

                hostHtml.writeFile(junitHtml.getBytes(DEFAULT_CHARSET));
            }
        }
        logger.outdent();
        return hostHtml;

    }

    private final static Charset DEFAULT_CHARSET = Charset.defaultCharset();

    /**
     * Starts up webdriver and loads the javascript host file which will run all the tests.
     */
    private void executeTestSuite(final J2clPath startupHostFile,
                                  final List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers,
                                  final int timeout,
                                  final J2clLinePrinter logger) throws Exception {

        logger.printLine("Test " + startupHostFile);
        logger.indent();
        {
            for (final J2clStepWorkerWebDriverUnitTestRunnerBrowser browser : browsers) {
                logger.printLine(browser.name());
                logger.indent();
                {
                    WebDriver driver = null;
                    try {
                        driver = browser.webDriver();
                        driver.get("file://" + startupHostFile);

                        // loop and poll if tests are done
                        new FluentWait<>(driver)
                                .withTimeout(Duration.ofSeconds(timeout))
                                .withMessage("Tests failed to finish in timeout")
                                .pollingEvery(Duration.ofMillis(100))
                                .until(d -> isFinished(d));

                        logger.printLine(executeScript(driver, "return window.G_testRunner.getReport()"));

                        // check for success
                        if (!isSuccess(driver)) {
                            throw new J2clException("One or more test(s) failed");
                        }
                        logger.printLine("All test(s) successful!");
                    } catch (final J2clException rethrow) {
                        throw rethrow;
                    } catch (final Exception cause) {
                        cause.printStackTrace();
                        logger.printLine("Test(s) failed!");
                        throw cause;
                    } finally {
                        if(null != driver) {
                            driver.quit();
                        }
                    }
                    logger.outdent();
                }
            }
        }
        logger.outdent();
    }

    private static boolean isSuccess(final WebDriver driver) {
        return executeScript(driver, "return window.G_testRunner.isSuccess()");
    }

    private static boolean isFinished(final WebDriver driver) {
        return executeScript(driver, "return !!(window.G_testRunner && window.G_testRunner.isFinished())");
    }

    private static <T> T executeScript(final WebDriver driver,
                                       final String script) {
        return (T)((JavascriptExecutor) driver).executeScript(script);
    }
}
