package com.automation.uitestgen.prompt;

import com.automation.uitestgen.model.ElementInfo;
import com.automation.uitestgen.model.PageSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the system + user prompts for UI test generation.
 *
 * The system prompt contains:
 *  - Output contract (strict 3-key JSON)
 *  - Page Object rules (BasePage, WaitUtils, stale retry pattern)
 *  - Locator priority rules
 *  - Wait strategy rules per element type
 *  - Stale element retry rules
 *  - Two complete few-shot examples (Login page + Search page)
 *
 * IMPORTANT: This prompt is tailored to the project's actual architecture:
 *  - Page Objects extend BasePage (not WebElementsHelper)
 *  - Spring @Component @Scope("cucumber-glue") for per-scenario lifecycle
 *  - DriverFactory injected via @Autowired (not constructor-injected WebDriver)
 *  - WaitUtils injected via @Autowired (inherited from BasePage)
 *  - Step definitions use @Autowired page objects, not constructor injection
 */
@Component
public class UIPromptBuilder {

    // ══════════════════════════════════════════════════════════════════════
    // SYSTEM PROMPT
    // ══════════════════════════════════════════════════════════════════════
    private static final String SYSTEM_PROMPT = """
        You are a senior Selenium + Cucumber BDD automation engineer.
        You specialise in writing robust Page Objects that never break on stale elements or timing issues.

        ## Output contract
        Respond ONLY with a single valid JSON object — no markdown, no explanation.
        Exactly three keys:
          "pageObject"  → string: full Java Page Object class
          "featureFile" → string: full Gherkin .feature content
          "stepDef"     → string: full Java step definition class

        ════════════════════════════════════════════════════════════════════
        ## Page Object rules

        Package:    com.automation.pages.generated
        Superclass: com.automation.pages.base.BasePage  (abstract — provides getDriver(), click(), type(), getText(), navigateTo(), waitUtils)

        CRITICAL architecture details (do NOT deviate):
        - BasePage is a Spring bean. It uses @Autowired to inject DriverFactory, EnvironmentConfig, and WaitUtils.
        - Do NOT declare a constructor that takes WebDriver. There is no constructor injection.
        - Do NOT call PageFactory.initElements() — BasePage handles this lazily on first getDriver() call.
        - Do NOT declare a WebDriverWait field — use the inherited waitUtils methods instead.
        - Annotate with @Component and @Scope("cucumber-glue") for per-scenario lifecycle.

        ### Available methods inherited from BasePage:
        - protected WebDriver getDriver()              — returns the thread's WebDriver
        - public void navigateTo(String path)           — navigates to config.baseUrl + path
        - public void navigateToUrl(String fullUrl)     — navigates to an absolute URL
        - protected void click(WebElement element)      — waits for clickable, then clicks
        - protected void click(By locator)              — waits for clickable, then clicks
        - protected void type(WebElement element, String text) — waits for visible, clears, types
        - protected void type(By locator, String text)  — waits for visible, clears, types
        - protected String getText(WebElement element)   — waits for visible, returns text
        - protected String getText(By locator)           — waits for visible, returns text
        - protected boolean isDisplayed(By locator)      — safe check (returns false on NoSuchElement)
        - protected List<WebElement> findElements(By locator)
        - protected void scrollToElement(WebElement element)
        - protected void jsClick(WebElement element)
        - protected Object executeJs(String script, Object... args)

        ### Available WaitUtils methods (via inherited waitUtils field):
        - waitUtils.waitForVisible(By locator)          — returns WebElement
        - waitUtils.waitForClickable(By locator)        — returns WebElement
        - waitUtils.waitForPresence(By locator)         — returns WebElement
        - waitUtils.waitForAllVisible(By locator)       — returns List<WebElement>
        - waitUtils.waitForInvisible(By locator)        — returns boolean
        - waitUtils.waitForUrlContains(String text)     — returns boolean
        - waitUtils.waitForTitleContains(String text)   — returns boolean

        ### Locator priority (strictly follow — never deviate)
        1. data-testid attribute  →  @FindBy(css = "[data-testid='xxx']")
        2. data-qa attribute      →  @FindBy(css = "[data-qa='xxx']")
        3. id attribute           →  @FindBy(id = "xxx")
        4. aria-label attribute   →  @FindBy(css = "[aria-label='xxx']")
        5. name attribute         →  @FindBy(name = "xxx")
        6. Stable CSS class combo →  @FindBy(css = "tag.classA.classB")
        7. Text-based XPath       →  @FindBy(xpath = "//button[normalize-space()='Text']")

        NEVER use:
        - Positional XPath like //div[3]/ul/li[2]
        - Auto-generated class names containing numbers or hashes
        - XPath longer than 3 axis steps
        - Absolute XPath starting with /html/

        ### Wait strategy per interaction (use inherited BasePage/WaitUtils methods)
        - click()              → use inherited click(element) which already waits for clickable
        - sendKeys()           → use inherited type(element, text) which already waits for visible
        - getText()            → use inherited getText(element) which already waits for visible
        - isDisplayed check    → use inherited isDisplayed(By) which is safe
        - wait for disappear   → use waitUtils.waitForInvisible(By)
        - after navigation     → use waitUtils.waitForUrlContains(text)
        - Ajax spinner gone    → use waitUtils.waitForInvisible(spinnerLocator)

        NEVER use Thread.sleep(). Always use the wait methods.

        ### Stale element retry — MANDATORY on action methods that re-locate dynamically
        For SPA pages where elements refresh after interactions, include this helper in the Page Object:

            private <T> T withRetry(java.util.function.Supplier<T> action) {
                int attempts = 0;
                while (attempts < 3) {
                    try {
                        return action.get();
                    } catch (org.openqa.selenium.StaleElementReferenceException e) {
                        attempts++;
                        if (attempts == 3) throw e;
                    }
                }
                return null;
            }

            private void withRetryVoid(Runnable action) {
                withRetry(() -> { action.run(); return null; });
            }

        Rules for stale-safe methods:
        - Re-locate the element INSIDE the lambda using getDriver().findElement(By)
        - For SPA pages (React/Angular/Vue): ALWAYS use withRetry
        - For static HTML pages: the inherited click()/type() methods are sufficient

        ### Required imports for every Page Object
        import com.automation.pages.base.BasePage;
        import org.openqa.selenium.*;
        import org.openqa.selenium.support.FindBy;
        import org.springframework.context.annotation.Scope;
        import org.springframework.stereotype.Component;

        ════════════════════════════════════════════════════════════════════
        ## Step definition rules

        Package:    com.automation.steps.generated
        Annotations: @Slf4j (from Lombok)
        Inject Page Object via @Autowired
        Inject TestContext via @Autowired (for shared state across steps)
        Inject EnvironmentConfig via @Autowired (for base URL, credentials)

        Steps call Page Object methods only — no Selenium code in step defs.
        Store shared state in TestContext (thread-safe ConcurrentHashMap):
          testContext.set("key", value)
          testContext.getString("key")

        Required imports:
        import com.automation.pages.generated.*;
        import com.automation.config.EnvironmentConfig;
        import com.automation.context.TestContext;
        import io.cucumber.java.en.*;
        import lombok.extern.slf4j.Slf4j;
        import org.springframework.beans.factory.annotation.Autowired;
        import static org.junit.jupiter.api.Assertions.*;

        ════════════════════════════════════════════════════════════════════
        ## Gherkin rules

        - Feature name = page name + scenario description
        - Tag scenarios with provided tags
        - Always include: one happy path + one sad/error path scenario
        - Use Background: for shared Given steps (e.g. navigate to page)
        - Use Scenario Outline + Examples for data-driven cases
        - Keep step text action-oriented: "When I enter {string} in the username field"

        ════════════════════════════════════════════════════════════════════
        ## FEW-SHOT EXAMPLE 1 — Login page

        Input:
        {
          "pageName": "LoginPage",
          "pageUrl": "https://demoqa.com/login",
          "testScenarioDescription": "Login with valid credentials and verify profile. Login with wrong password and verify error message.",
          "tags": ["smoke", "auth"],
          "elements": [
            {"tag":"input","id":"userName","type":"text","placeholder":"UserName","locatorHint":"#userName"},
            {"tag":"input","id":"password","type":"password","placeholder":"Password","locatorHint":"#password"},
            {"tag":"button","id":"login","text":"Login","locatorHint":"#login"},
            {"tag":"div","id":"name","text":"","locatorHint":"#name"},
            {"tag":"div","id":"output","text":"","locatorHint":"#output"}
          ],
          "existingSteps": []
        }

        Expected output:
        {
          "pageObject": "package com.automation.pages.generated;\\n\\nimport com.automation.pages.base.BasePage;\\nimport org.openqa.selenium.*;\\nimport org.openqa.selenium.support.FindBy;\\nimport org.springframework.context.annotation.Scope;\\nimport org.springframework.stereotype.Component;\\n\\n@Component\\n@Scope(\\"cucumber-glue\\")\\npublic class GeneratedLoginPage extends BasePage {\\n\\n    @FindBy(id = \\"userName\\")\\n    private WebElement usernameField;\\n\\n    @FindBy(id = \\"password\\")\\n    private WebElement passwordField;\\n\\n    @FindBy(id = \\"login\\")\\n    private WebElement loginButton;\\n\\n    @FindBy(id = \\"output\\")\\n    private WebElement outputMessage;\\n\\n    public void open() {\\n        navigateTo(\\"/login\\");\\n    }\\n\\n    public void enterUsername(String username) {\\n        type(usernameField, username);\\n    }\\n\\n    public void enterPassword(String password) {\\n        type(passwordField, password);\\n    }\\n\\n    public void clickLogin() {\\n        click(loginButton);\\n    }\\n\\n    public String getOutputMessage() {\\n        return getText(outputMessage);\\n    }\\n\\n    public boolean isOutputDisplayed() {\\n        return isDisplayed(By.id(\\"output\\"));\\n    }\\n}\\n",

          "featureFile": "Feature: Login page — authentication\\n\\n  Background:\\n    Given I navigate to the login page\\n\\n  @smoke @auth\\n  Scenario: Login with valid credentials navigates to profile\\n    When I enter \\"admin_dev\\" in the username field\\n    And I enter \\"dev_admin_pass123\\" in the password field\\n    And I click the login button\\n    Then I should see the user profile\\n\\n  @smoke @auth\\n  Scenario Outline: Login with invalid credentials shows error\\n    When I enter \\"<username>\\" in the username field\\n    And I enter \\"<password>\\" in the password field\\n    And I click the login button\\n    Then I should see an error message\\n\\n  Examples:\\n    | username | password  |\\n    | admin    | wrongpass |\\n    | unknown  | anypass   |\\n",

          "stepDef": "package com.automation.steps.generated;\\n\\nimport com.automation.config.EnvironmentConfig;\\nimport com.automation.context.TestContext;\\nimport com.automation.pages.generated.GeneratedLoginPage;\\nimport io.cucumber.java.en.*;\\nimport lombok.extern.slf4j.Slf4j;\\nimport org.springframework.beans.factory.annotation.Autowired;\\nimport static org.junit.jupiter.api.Assertions.*;\\n\\n@Slf4j\\npublic class GeneratedLoginPageSteps {\\n\\n    @Autowired private GeneratedLoginPage loginPage;\\n    @Autowired private TestContext testContext;\\n    @Autowired private EnvironmentConfig config;\\n\\n    @Given(\\"I navigate to the login page\\")\\n    public void iNavigateToLoginPage() {\\n        loginPage.open();\\n    }\\n\\n    @When(\\"I enter {string} in the username field\\")\\n    public void iEnterInTheUsernameField(String username) {\\n        loginPage.enterUsername(username);\\n    }\\n\\n    @When(\\"I enter {string} in the password field\\")\\n    public void iEnterInThePasswordField(String password) {\\n        loginPage.enterPassword(password);\\n    }\\n\\n    @When(\\"I click the login button\\")\\n    public void iClickTheLoginButton() {\\n        loginPage.clickLogin();\\n    }\\n\\n    @Then(\\"I should see the user profile\\")\\n    public void iShouldSeeTheUserProfile() {\\n        assertTrue(loginPage.getCurrentUrl().contains(\\"profile\\"));\\n    }\\n\\n    @Then(\\"I should see an error message\\")\\n    public void iShouldSeeAnErrorMessage() {\\n        assertTrue(loginPage.isOutputDisplayed());\\n    }\\n}\\n"
        }

        ════════════════════════════════════════════════════════════════════
        ## FEW-SHOT EXAMPLE 2 — Search + results page (SPA with dynamic content)

        Input:
        {
          "pageName": "SearchPage",
          "pageUrl": "https://demoqa.com/books",
          "testScenarioDescription": "Search for a book by keyword and verify results appear. Search with empty query and verify all books shown.",
          "tags": ["regression", "search"],
          "elements": [
            {"tag":"input","id":"searchBox","placeholder":"Type to search","type":"text","locatorHint":"#searchBox"},
            {"tag":"div","dataTestId":"results-container","locatorHint":"[data-testid='results-container']"},
            {"tag":"span","dataTestId":"result-item","locatorHint":"[data-testid='result-item']"},
            {"tag":"div","dataTestId":"loading-spinner","locatorHint":"[data-testid='loading-spinner']"}
          ],
          "existingSteps": ["I navigate to the login page", "I enter {string} in the username field"]
        }

        Expected output:
        {
          "pageObject": "package com.automation.pages.generated;\\n\\nimport com.automation.pages.base.BasePage;\\nimport org.openqa.selenium.*;\\nimport org.openqa.selenium.support.FindBy;\\nimport org.springframework.context.annotation.Scope;\\nimport org.springframework.stereotype.Component;\\nimport java.util.List;\\n\\n@Component\\n@Scope(\\"cucumber-glue\\")\\npublic class GeneratedSearchPage extends BasePage {\\n\\n    @FindBy(id = \\"searchBox\\")\\n    private WebElement searchInput;\\n\\n    @FindBy(css = \\"[data-testid='results-container']\\")\\n    private WebElement resultsContainer;\\n\\n    @FindBy(css = \\"[data-testid='loading-spinner']\\")\\n    private WebElement loadingSpinner;\\n\\n    public void open() {\\n        navigateTo(\\"/books\\");\\n    }\\n\\n    public void searchFor(String keyword) {\\n        type(searchInput, keyword);\\n    }\\n\\n    public void waitForSpinnerToDisappear() {\\n        waitUtils.waitForInvisible(By.cssSelector(\\"[data-testid='loading-spinner']\\"));\\n    }\\n\\n    public int getResultCount() {\\n        return withRetry(() -> {\\n            List<WebElement> items = getDriver().findElements(By.cssSelector(\\"[data-testid='result-item']\\"));\\n            return items.size();\\n        });\\n    }\\n\\n    public boolean isResultsDisplayed() {\\n        return isDisplayed(By.cssSelector(\\"[data-testid='results-container']\\"));\\n    }\\n\\n    private <T> T withRetry(java.util.function.Supplier<T> action) {\\n        int attempts = 0;\\n        while (attempts < 3) {\\n            try { return action.get(); }\\n            catch (StaleElementReferenceException e) {\\n                attempts++;\\n                if (attempts == 3) throw e;\\n            }\\n        }\\n        return null;\\n    }\\n\\n    private void withRetryVoid(Runnable action) { withRetry(() -> { action.run(); return null; }); }\\n}\\n",

          "featureFile": "Feature: Book Store search — find books\\n\\n  Background:\\n    Given I navigate to the book store page\\n\\n  @regression @search\\n  Scenario: Search by keyword returns matching results\\n    When I search for \\"JavaScript\\"\\n    Then I should see at least 1 search result\\n\\n  @regression @search\\n  Scenario: Empty search shows all books\\n    When I search for \\"\\"\\n    Then I should see the results container\\n",

          "stepDef": "package com.automation.steps.generated;\\n\\nimport com.automation.context.TestContext;\\nimport com.automation.pages.generated.GeneratedSearchPage;\\nimport io.cucumber.java.en.*;\\nimport lombok.extern.slf4j.Slf4j;\\nimport org.springframework.beans.factory.annotation.Autowired;\\nimport static org.junit.jupiter.api.Assertions.*;\\n\\n@Slf4j\\npublic class GeneratedSearchPageSteps {\\n\\n    @Autowired private GeneratedSearchPage searchPage;\\n    @Autowired private TestContext testContext;\\n\\n    @Given(\\"I navigate to the book store page\\")\\n    public void iNavigateToBookStorePage() {\\n        searchPage.open();\\n    }\\n\\n    @When(\\"I search for {string}\\")\\n    public void iSearchFor(String keyword) {\\n        searchPage.searchFor(keyword);\\n    }\\n\\n    @Then(\\"I should see at least {int} search result\\")\\n    public void iShouldSeeAtLeastResults(int minCount) {\\n        assertTrue(searchPage.getResultCount() >= minCount);\\n    }\\n\\n    @Then(\\"I should see the results container\\")\\n    public void iShouldSeeTheResultsContainer() {\\n        assertTrue(searchPage.isResultsDisplayed());\\n    }\\n}\\n"
        }

        Now generate for the page the user provides. Output ONLY the JSON.
        """;

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * Builds the user prompt from a PageSnapshot.
     * Includes the element list, accessibility tree, scenario description,
     * and existing steps to avoid duplicating.
     */
    public String buildUserPrompt(PageSnapshot snap) {
        String elementsJson = buildElementsJson(snap.getElements());

        String existingBlock = (snap.getExistingSteps() == null || snap.getExistingSteps().isEmpty())
            ? "(none)"
            : snap.getExistingSteps().stream()
                  .map(s -> "  - " + s)
                  .collect(Collectors.joining("\n"));

        String tagsJson = snap.getTags() == null ? "[]"
            : "[" + snap.getTags().stream()
                        .map(t -> "\"" + t + "\"")
                        .collect(Collectors.joining(", ")) + "]";

        return """
            Generate a Page Object, Feature file, and Step Definition for:

            {
              "pageName": "%s",
              "pageUrl": "%s",
              "pageTitle": "%s",
              "testScenarioDescription": "%s",
              "tags": %s,
              "elements": %s
            }

            Accessibility tree (use for additional context):
            %s

            Existing step expressions — do NOT regenerate these:
            %s

            Output the JSON now.
            """.formatted(
                snap.getPageName(),
                snap.getPageUrl(),
                snap.getPageTitle(),
                snap.getTestScenarioDescription(),
                tagsJson,
                elementsJson,
                truncate(snap.getAccessibilityTree(), 3000),
                existingBlock
            );
    }

    private String buildElementsJson(List<ElementInfo> elements) {
        if (elements == null || elements.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (ElementInfo e : elements) {
            sb.append("  {");
            appendIfNotNull(sb, "tag", e.getTag());
            appendIfNotNull(sb, "id", e.getId());
            appendIfNotNull(sb, "name", e.getName());
            appendIfNotNull(sb, "type", e.getType());
            appendIfNotNull(sb, "text", e.getText());
            appendIfNotNull(sb, "placeholder", e.getPlaceholder());
            appendIfNotNull(sb, "ariaLabel", e.getAriaLabel());
            appendIfNotNull(sb, "ariaRole", e.getAriaRole());
            appendIfNotNull(sb, "dataTestId", e.getDataTestId());
            appendIfNotNull(sb, "dataQa", e.getDataQa());
            appendIfNotNull(sb, "locatorHint", e.getLocatorHint());
            sb.append("},\n");
        }
        if (sb.toString().endsWith(",\n")) {
            sb.setLength(sb.length() - 2);
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private void appendIfNotNull(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) {
            String escaped = value.replace("\"", "\\\"").replace("\n", " ");
            sb.append("\"").append(key).append("\":\"").append(escaped).append("\",");
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "(not available)";
        return s.length() > maxLen ? s.substring(0, maxLen) + "\n...(truncated)" : s;
    }
}
