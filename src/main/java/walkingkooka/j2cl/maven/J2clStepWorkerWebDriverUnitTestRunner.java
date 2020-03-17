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
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.FluentWait;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;

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
        final J2clPath junitStartup;

        logger.printLine("Prepare host file");
        logger.indent();
        {
            final J2clPath file = request.initialScriptFilename();
            junitStartup = file.parent().append(CharSequences.subSequence(file.file().getName(), 0, -2) + "html");

            logger.printIndented("Compiled tests file", file);
            logger.printIndented("JUnit startup file", junitStartup);

            try (final InputStreamReader reader = new InputStreamReader(this.getClass().getResourceAsStream("junit.html"), DEFAULT_CHARSET)) {
                final String junitHtml = CharStreams.toString(reader)
                        .replace("<TEST_SCRIPT>", file.toString());

                junitStartup.writeFile(junitHtml.getBytes(DEFAULT_CHARSET));
            }
        }
        logger.outdent();
        return junitStartup;

    }

    private final static Charset DEFAULT_CHARSET = Charset.defaultCharset();

    /**
     * Starts up webdriver and loads the javascript host file which will run all the tests.
     */
    private void executeTestSuite(final J2clPath startupHostFile,
                                  final int timeout,
                                  final J2clLinePrinter logger) throws Exception {

        logger.printLine("Test " + startupHostFile);
        logger.indent();
        {
            WebDriver driver = null;
            try {
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(new ChromeOptions().setHeadless(true));
                driver.get("file://" + startupHostFile);

                // loop and poll if tests are done
                new FluentWait<>(driver)
                        .withTimeout(Duration.ofSeconds(timeout))
                        .withMessage("Tests failed to finish in timeout")
                        .pollingEvery(Duration.ofMillis(100))
                        .until(d -> isFinished(d));

                logger.printLine(executeScript(driver, "window.G_testRunner.getReport()"));

                // check for success
                if (!isSuccess(driver)) {
                    logger.printLine("One or more test(s) failed!");
                } else {
                    logger.printLine("All test(s) successful!");
                }
            } catch (final Exception cause) {
                cause.printStackTrace();
                logger.printLine("Test(s) failed!");
                throw cause;
            } finally {
                if (driver != null) {
                    driver.quit();
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
