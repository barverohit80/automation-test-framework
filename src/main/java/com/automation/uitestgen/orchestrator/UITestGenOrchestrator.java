package com.automation.uitestgen.orchestrator;

import com.automation.driver.DriverFactory;
import com.automation.uitestgen.capture.UISpecCapture;
import com.automation.uitestgen.client.LlmClient;
import com.automation.uitestgen.model.GeneratedUITest;
import com.automation.uitestgen.model.PageSnapshot;
import com.automation.uitestgen.prompt.UIPromptBuilder;
import com.automation.uitestgen.writer.UITestFileWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Main entry point for AI-powered UI test generation.
 *
 * Pipeline: capture page DOM → build prompt → call Claude → parse response → write files.
 *
 * Usage from a test setup, @Before hook, or standalone:
 *
 *   PageSnapshot snap = new PageSnapshot();
 *   snap.setPageName("LoginPage");
 *   snap.setTestScenarioDescription("Login with valid credentials ...");
 *   snap.setTags(List.of("smoke", "auth"));
 *   snap.setExistingSteps(List.of("I navigate to the home page"));
 *
 *   GeneratedUITest result = orchestrator.generate(driver, snap);
 *   // Files written automatically to configured output dirs
 *
 * Or use the convenience overload without a WebDriver parameter
 * (uses the current thread's driver from DriverFactory):
 *
 *   GeneratedUITest result = orchestrator.generate(snap);
 */
@Slf4j
@Service
public class UITestGenOrchestrator {

    @Autowired private UISpecCapture capture;
    @Autowired private UIPromptBuilder promptBuilder;
    @Autowired private LlmClient llmClient;
    @Autowired private UITestFileWriter fileWriter;
    @Autowired private DriverFactory driverFactory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${testgen.pageobject.output-dir:src/main/java/com/automation/pages/generated}")
    private String pageObjectDir;

    @Value("${testgen.feature.output-dir:src/main/resources/features/generated}")
    private String featureDir;

    @Value("${testgen.stepdef.output-dir:src/main/java/com/automation/steps/generated}")
    private String stepDefDir;

    /**
     * Full pipeline: capture page → build prompt → call Claude → write files.
     *
     * @param driver live WebDriver on the page to generate tests for
     * @param snap   pre-filled with pageName, scenario description, tags, existingSteps
     */
    public GeneratedUITest generate(WebDriver driver, PageSnapshot snap) throws Exception {
        log.info("[UITestGen] -> Capturing page: {}", snap.getPageName());

        // 1. Capture DOM, a11y tree, screenshot
        PageSnapshot enriched = capture.capture(driver, snap.getPageName());
        enriched.setTestScenarioDescription(snap.getTestScenarioDescription());
        enriched.setTags(snap.getTags());
        enriched.setExistingSteps(snap.getExistingSteps());

        log.info("[UITestGen] -> Captured {} elements", enriched.getElements().size());
        log.info("[UITestGen] -> Building prompt...");

        // 2. Build prompts
        String systemPrompt = promptBuilder.getSystemPrompt();
        String userPrompt   = promptBuilder.buildUserPrompt(enriched);

        log.info("[UITestGen] -> Calling LLM provider: {}", llmClient.providerName());

        // 3. Call LLM (use vision mode if screenshot available and HTML is thin)
        String rawResponse;
        if (enriched.getScreenshotBase64() != null
                && enriched.getFilteredHtml() != null
                && enriched.getFilteredHtml().length() < 500) {
            rawResponse = llmClient.generateWithScreenshot(systemPrompt, userPrompt, enriched.getScreenshotBase64());
        } else {
            rawResponse = llmClient.generate(systemPrompt, userPrompt);
        }

        log.info("[UITestGen] -> Parsing response...");

        // 4. Parse
        GeneratedUITest result = parse(rawResponse);

        // 5. Write files
        String safeName = snap.getPageName().replaceAll("[^a-zA-Z0-9]", "");
        result.setPageObjectFilePath(
            fileWriter.write(result.getPageObjectContent(),
                pageObjectDir + "/Generated" + safeName + ".java", "Page Object"));
        result.setFeatureFilePath(
            fileWriter.write(result.getFeatureContent(),
                featureDir + "/" + safeName.toLowerCase() + "_generated.feature", "Feature file"));
        result.setStepDefFilePath(
            fileWriter.write(result.getStepDefContent(),
                stepDefDir + "/Generated" + safeName + "Steps.java", "Step definition"));

        log.info("[UITestGen] Done: {}", safeName);
        return result;
    }

    /**
     * Convenience overload — uses the current thread's WebDriver from DriverFactory.
     */
    public GeneratedUITest generate(PageSnapshot snap) throws Exception {
        return generate(driverFactory.getDriver(), snap);
    }

    private GeneratedUITest parse(String raw) throws Exception {
        String clean = raw.replaceAll("```json|```", "").trim();
        JsonNode node = mapper.readTree(clean);

        GeneratedUITest result = new GeneratedUITest();
        result.setPageObjectContent(node.get("pageObject").asText());
        result.setFeatureContent(node.get("featureFile").asText());
        result.setStepDefContent(node.get("stepDef").asText());
        return result;
    }
}
