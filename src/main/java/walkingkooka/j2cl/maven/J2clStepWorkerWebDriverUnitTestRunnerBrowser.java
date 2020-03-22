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

import com.gargoylesoftware.htmlunit.BrowserVersion;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import walkingkooka.text.CharSequences;

import java.util.Arrays;
import java.util.stream.Collectors;


/**
 * Lists all available or supported browsers, with a single factory method for each to create a {@link WebDriver} instance.
 */
enum J2clStepWorkerWebDriverUnitTestRunnerBrowser {
    CHROME {
        @Override
        WebDriver webDriver() {
            WebDriverManager.chromedriver().setup();
            return new ChromeDriver(new ChromeOptions().setHeadless(true));
        }

    },
    FIREFOX {
        @Override
        WebDriver webDriver() {
            WebDriverManager.firefoxdriver().setup();
            return new FirefoxDriver(new FirefoxOptions().setHeadless(true));
        }

    },
    HTML_UNIT {
        @Override
        WebDriver webDriver() {
            return new HtmlUnitDriver(BrowserVersion.BEST_SUPPORTED, true);
        }
    };

    abstract WebDriver webDriver() throws Exception;

    static J2clStepWorkerWebDriverUnitTestRunnerBrowser fromCommandLine(final String option) {
        return Arrays.stream(values())
                .filter(e -> e.name().equals(option) || e.name().toLowerCase().equals(option))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown browser option " + CharSequences.quote(option) + " expected one of " +
                        Arrays.stream(J2clStepWorkerWebDriverUnitTestRunnerBrowser.values()).map(Enum::name).collect(Collectors.joining(", "))));
    }
}
