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
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.FluentWait;
import walkingkooka.j2cl.maven.log.BrowserLogLevel;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.text.CharSequences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;

/**
 * Assumes that the closure compiler has completed successfully and then invokes web driver to execute the prepared
 * js file containing the tests.
 */
final class J2clStepWorkerWebDriverUnitTestRunner implements J2clStepWorker {

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
    public J2clStepResult execute(final J2clDependency artifact,
                                  final J2clStep step,
                                  final TreeLogger logger) throws Exception {
        return this.executeIfNecessary(
                artifact,
                step,
                logger
        );
    }

    @Override
    public J2clStepResult executeWithDirectory(final J2clDependency artifact,
                                               final J2clStepDirectory directory,
                                               final TreeLogger logger) throws Exception {
        logger.line("Junit Tests");
        logger.indent();
        {
            final J2clMavenContext context = artifact.context();
            this.executeTestSuite(this.prepareJunitHostFileScriptPath(context, logger),
                    context.browsers(),
                    context.browserLogLevel(),
                    context.testTimeout(),
                    logger);
        }
        logger.outdent();

        return J2clStepResult.SUCCESS;
    }

    /**
     * Loads the html file and replaces the script file with the closure compiled test file.
     */
    private J2clPath prepareJunitHostFileScriptPath(final J2clMavenContext context,
                                                    final TreeLogger logger) throws IOException {
        final J2clPath hostHtml;

        logger.line("Prepare host file");
        logger.indent();
        {
            final J2clPath file = context.initialScriptFilename();
            hostHtml = file.parent()
                    .append(CharSequences.subSequence(file.file().getName(), 0, -2) + "html");

            logger.path("Compiled tests file", file);
            logger.path("JUnit html host file", hostHtml);

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
                                  final BrowserLogLevel logLevel,
                                  final int timeout,
                                  final TreeLogger logger) throws Exception {

        logger.line("Test " + startupHostFile);
        logger.indent();
        {
            for (final J2clStepWorkerWebDriverUnitTestRunnerBrowser browser : browsers) {
                logger.line(browser.name());
                logger.indent();
                {
                    WebDriver driver = null;
                    try {
                        driver = browser.webDriver(logLevel);
                        driver.get("file://" + startupHostFile);

                        // loop and poll if tests are done
                        new FluentWait<>(driver)
                                .withTimeout(Duration.ofSeconds(timeout))
                                .withMessage("Tests failed to finish in timeout")
                                .pollingEvery(Duration.ofMillis(100))
                                .until(d -> isFinished(d));

                        final String testReport = testReport(driver);
                        logger.indent();
                        {
                            printTestReport(logger, testReport);
                            printBrowserLogs(driver, logLevel, logger);
                        }
                        logger.outdent();

                        // check for success
                        if (!isSuccess(driver)) {
                            throw new J2clException(testsFailedMessage(testReport));
                        }
                        logger.line("All test(s) successful!");
                    } catch (final J2clException rethrow) {
                        throw rethrow;
                    } catch (final Exception cause) {
                        cause.printStackTrace();
                        logger.line("Test(s) failed!");
                        throw cause;
                    } finally {
                        if (null != driver) {
                            driver.quit();
                        }
                    }
                    logger.outdent();
                }
            }
        }
        logger.outdent();
    }

    private static String testReport(final WebDriver driver) {
        return executeScript(driver, "return window.G_testRunner.getReport()");
    }

    private static boolean isSuccess(final WebDriver driver) {
        return executeScript(driver, "return window.G_testRunner.isSuccess()");
    }

    /**
     * <pre>
     * 4 of 4 tests run in 5.874999973457307ms.
     * 2 passed, 2 failed.
     * 1 ms/test. 1 files loaded.
     * ERROR in testSimple3
     * </pre>
     */
    private static String testsFailedMessage(final String testReport) throws IOException {
        String message = "One or more test(s) failed";

        try (final BufferedReader reader = new BufferedReader(new StringReader(testReport))) {
            for (; ; ) {
                final String line = reader.readLine();
                if (null == line) {
                    break; // EOF
                }

                if (line.contains("failed")) {
                    message = line.trim();
                    break;
                }
            }
        }

        return message;
    }

    private static boolean isFinished(final WebDriver driver) {
        return executeScript(driver, "return !!(window.G_testRunner && window.G_testRunner.isFinished())");
    }

    private static <T> T executeScript(final WebDriver driver,
                                       final String script) {
        return (T) ((JavascriptExecutor) driver).executeScript(script);
    }

    private static void printTestReport(final TreeLogger logger,
                                        final String testReport) {
        try {
            logger.line("Test Report");
            logger.indent();
            logger.line(testReport);
        } finally {
            logger.outdent();
        }
    }

    /**
     * Prints all the log messages from the browser, doing nothing when {@link BrowserLogLevel#NONE}.
     */
    private static void printBrowserLogs(final WebDriver driver,
                                         final BrowserLogLevel logLevel,
                                         final TreeLogger logger) {
        switch (logLevel) {
            case NONE:
                break;
            default: {
                logger.line("Browser log");
                logger.indent();
                {
                    for (final LogEntry entry : driver.manage()
                            .logs()
                            .get(LogType.BROWSER)) {
                        logger.line(entry.getLevel() + " " + entry.getMessage());
                    }
                }
                logger.outdent();
            }
        }
    }
}
