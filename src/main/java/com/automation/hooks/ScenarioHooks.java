package com.automation.hooks;

import com.automation.config.EnvironmentConfig;
import com.automation.context.ScenarioContext;
import com.automation.context.TestContext;
import com.automation.driver.DriverFactory;
import com.automation.utils.ScreenshotUtils;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;

/**
 * Cucumber hooks — manages driver lifecycle per scenario.
 *
 * Session Strategy:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  @api scenario:                                                 │
 * │    @Before  → NO browser — pure REST/API test                   │
 * │    @After   → clear TestContext only (no driver cleanup)        │
 * │                                                                │
 * │  Normal scenario (no @newbrowser tag):                         │
 * │    @Before  → reuse existing browser session (or create one)   │
 * │    @After   → clear cookies + navigate to blank (keep alive)   │
 * │                                                                │
 * │  @newbrowser scenario:                                         │
 * │    @Before  → kill existing session + launch FRESH browser     │
 * │    @After   → kill the fresh session completely                │
 * │                                                                │
 * │  Next normal scenario after @newbrowser:                       │
 * │    @Before  → no session exists → creates new shared session   │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Allure Labels:
 *   Browser-based segregation is done via Allure.parameter("Browser", ...).
 *   The Allure Cucumber7 plugin controls parentSuite/suite labels and overwrites
 *   any hook-based changes. So the actual label rewriting happens in
 *   AutomationApplication.postProcessAllureResults() which modifies the result
 *   JSON files AFTER execution, BEFORE report generation.
 */
@Slf4j
public class ScenarioHooks {

    private static final String NEW_BROWSER_TAG = "@newbrowser";
    private static final String API_TAG = "@api";

    @Autowired private DriverFactory driverFactory;
    @Autowired private TestContext testContext;
    @Autowired private ScenarioContext scenarioContext;
    @Autowired private ScreenshotUtils screenshotUtils;
    @Autowired private EnvironmentConfig config;

    @Before(order = 0)
    public void setUp(Scenario scenario) {
        boolean isApiTest = scenario.getSourceTagNames().contains(API_TAG);
        boolean needsNewBrowser = scenario.getSourceTagNames().contains(NEW_BROWSER_TAG);

        log.info("======== SCENARIO START: {} ========", scenario.getName());

        // ── API tests: no browser needed ──
        if (isApiTest) {
            log.info("Env: {} | Type: API | Thread: {}",
                    config.getEnvironment(), Thread.currentThread().getId());

            // Parameter survives Allure plugin overrides — used by post-processor
            Allure.parameter("Browser", "API");
            Allure.parameter("Environment", config.getEnvironment());
            Allure.parameter("Thread", String.valueOf(Thread.currentThread().getId()));

            scenarioContext.init(scenario, "api");
            return;
        }

        // ── UI tests: manage browser lifecycle ──
        log.info("Env: {} | Browser: {} | Thread: {} | Session: {}",
                config.getEnvironment(),
                driverFactory.getTargetBrowser(),
                Thread.currentThread().getId(),
                needsNewBrowser ? "FRESH (@newbrowser)" : "SHARED (reuse)");

        if (needsNewBrowser) {
            driverFactory.forceNewDriver();
        } else {
            driverFactory.getDriver();
        }

        // Set browser parameter — this is the SOURCE OF TRUTH for Allure post-processing.
        // The Allure Cucumber7 plugin overwrites parentSuite/suite labels, so we can't
        // set them here. Instead, AutomationApplication.postProcessAllureResults() reads
        // the "Browser" parameter from each result JSON and rewrites the labels.
        String browser = driverFactory.getActualBrowserName();
        Allure.parameter("Browser", browser);
        Allure.parameter("Environment", config.getEnvironment());
        Allure.parameter("Thread", String.valueOf(Thread.currentThread().getId()));

        scenarioContext.init(scenario, browser);
    }

    @After(order = 0)
    public void tearDown(Scenario scenario) {
        boolean isApiTest = scenario.getSourceTagNames().contains(API_TAG);
        boolean wasNewBrowser = driverFactory.isForceNewSession();

        try {
            // ── Screenshot for UI tests (pass or fail) ──
            if (!isApiTest && driverFactory.hasDriver()) {
                String status = scenario.isFailed() ? "FAILED" : "PASSED";
                String label = scenario.getName() + " — " + status.toLowerCase() + " screenshot";

                if (scenario.isFailed()) {
                    log.warn("Scenario FAILED: {} — capturing screenshot", scenario.getName());
                } else {
                    log.info("Scenario PASSED: {} — capturing screenshot", scenario.getName());
                }

                byte[] screenshot = screenshotUtils.captureScreenshot();
                if (screenshot.length > 0) {
                    scenarioContext.attachScreenshot(screenshot);
                    Allure.addAttachment(
                            label,
                            "image/png",
                            new ByteArrayInputStream(screenshot),
                            "png");
                    screenshotUtils.saveScreenshot(scenario.getName() + "_" + status);
                }
            }
        } catch (Exception e) {
            log.error("Error capturing screenshot: {}", e.getMessage());
        } finally {
            testContext.clear();

            // ── API tests: no driver cleanup needed ──
            if (isApiTest) {
                log.info("======== SCENARIO {} [api]: {} ========\n",
                        scenario.isFailed() ? "FAILED" : "PASSED",
                        scenario.getName());
                return;
            }

            // ── UI tests: manage browser lifecycle ──
            if (wasNewBrowser) {
                log.info("[Thread-{}] @newbrowser teardown → quitting fresh session",
                        Thread.currentThread().getId());
                driverFactory.quitDriver();
                driverFactory.clearForceNewFlag();
            } else {
                driverFactory.cleanupSession();
            }

            log.info("======== SCENARIO {} [{}]: {} ========\n",
                    scenario.isFailed() ? "FAILED" : "PASSED",
                    wasNewBrowser ? "fresh-session" : "shared-session",
                    scenario.getName());
        }
    }
}
