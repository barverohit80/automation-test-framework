package com.automation.hooks;

import com.automation.config.EnvironmentConfig;
import com.automation.context.ScenarioContext;
import com.automation.context.TestContext;
import com.automation.driver.DriverFactory;
import com.automation.uitestgen.model.GeneratedUITest;
import com.automation.uitestgen.model.PageSnapshot;
import com.automation.uitestgen.orchestrator.UITestGenOrchestrator;
import com.automation.utils.ScreenshotUtils;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    private static final String GENERATE_TAG = "@generate";

    @Autowired private DriverFactory driverFactory;
    @Autowired private TestContext testContext;
    @Autowired private ScenarioContext scenarioContext;
    @Autowired private ScreenshotUtils screenshotUtils;
    @Autowired private EnvironmentConfig config;
    @Autowired private UITestGenOrchestrator uiTestGenOrchestrator;

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

    /**
     * Generates UI test artifacts (Page Object, Feature file, Step Definitions) via Claude
     * before the scenario executes. Triggered only for scenarios tagged with @generate.
     *
     * Runs at order=1 so the driver is already initialised by setUp (order=0).
     *
     * Required scenario tags:
     *   @generate                          — activates this hook
     *   @page:<PageName>                   — logical page name, e.g. @page:LoginPage
     *   @url:<encoded-url>                 — target page URL (colons after scheme must be present)
     *
     * Optional scenario tags:
     *   @tag:<cucumber-tag>                — extra Cucumber tags on generated scenarios (repeatable)
     *
     * The scenario name is used as the testScenarioDescription sent to the LLM.
     */
    @Before(value = GENERATE_TAG, order = 1)
    public void generateUITest(Scenario scenario) {
        log.info("[UITestGen] @generate hook triggered for scenario: {}", scenario.getName());

        Collection<String> tags = scenario.getSourceTagNames();

        // ── Extract pageName from @page:<Name> tag ──
        String pageName = tags.stream()
                .filter(t -> t.startsWith("@page:"))
                .map(t -> t.substring("@page:".length()))
                .findFirst()
                .orElse(derivePageName(scenario.getUri()));

        // ── Extract target URL from @url:<url> tag (optional) ──
        String pageUrl = tags.stream()
                .filter(t -> t.startsWith("@url:"))
                .map(t -> t.substring("@url:".length()))
                .findFirst()
                .orElse(null);

        // ── Collect extra Cucumber tags from @tag:<value> tags ──
        List<String> cucumberTags = tags.stream()
                .filter(t -> t.startsWith("@tag:"))
                .map(t -> t.substring("@tag:".length()))
                .collect(java.util.stream.Collectors.toList());

        // ── Navigate to the page if a URL was provided ──
        if (pageUrl != null) {
            log.info("[UITestGen] Navigating to {} before capture", pageUrl);
            driverFactory.getDriver().get(pageUrl);
        }

        // ── Build PageSnapshot with all required details ──
        PageSnapshot snap = new PageSnapshot();
        snap.setPageName(pageName);
        snap.setPageUrl(pageUrl);
        snap.setTestScenarioDescription(scenario.getName());
        snap.setTags(cucumberTags);
        snap.setExistingSteps(new ArrayList<>());

        try {
            GeneratedUITest result = uiTestGenOrchestrator.generate(snap);

            log.info("[UITestGen] Generated Page Object : {}", result.getPageObjectFilePath());
            log.info("[UITestGen] Generated Feature file: {}", result.getFeatureFilePath());
            log.info("[UITestGen] Generated Step Defs   : {}", result.getStepDefFilePath());

            if (result.getConflicts() != null && !result.getConflicts().isEmpty()) {
                log.warn("[UITestGen] Duplicate step warnings: {}", result.getConflicts());
            }

            // Store the result in TestContext so step definitions can access it
            testContext.set("generatedUITest", result);
        } catch (Exception e) {
            log.error("[UITestGen] Test generation failed for page '{}': {}", pageName, e.getMessage(), e);
            throw new RuntimeException("UI test generation failed for page '" + pageName + "'", e);
        }
    }

    /**
     * Derives a PascalCase page name from the feature file URI.
     * E.g. "file:///path/to/login_credentials.feature" → "LoginCredentialsPage"
     */
    private String derivePageName(java.net.URI featureUri) {
        String path = featureUri.getPath();
        // Extract filename without extension
        String fileName = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.feature$", "");
        // Split on underscores, hyphens, or spaces and convert to PascalCase
        StringBuilder sb = new StringBuilder();
        for (String word : fileName.split("[_\\-\\s]+")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase());
                }
            }
        }
        String name = sb.toString().replaceAll("[^a-zA-Z0-9]", "");
        return name.endsWith("Page") ? name : name + "Page";
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
