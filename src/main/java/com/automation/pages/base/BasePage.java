package com.automation.pages.base;

import com.automation.config.EnvironmentConfig;
import com.automation.driver.DriverFactory;
import com.automation.locator.strategy.ResilientLocatorStrategy;
import com.automation.utils.WaitUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Base page for all Page Objects.
 *
 * Uses Selenium PageFactory to initialize @FindBy elements.
 * Subclasses are Spring beans — just annotate with @Component @Scope("cucumber-glue")
 * and their @FindBy fields are auto-initialized.
 *
 * Also provides resilient (self-healing) locator methods via {@link ResilientLocatorStrategy}.
 * Use {@code resilientClick("pageName", "elementName")} etc. for locators loaded from
 * JSON files at resources/locators/, with automatic primary → fallback healing.
 */
@Slf4j
public abstract class BasePage {

    @Autowired
    protected DriverFactory driverFactory;

    @Autowired
    protected EnvironmentConfig config;

    @Autowired
    protected WaitUtils waitUtils;

    @Autowired
    protected ResilientLocatorStrategy resilientLocator;

    /** Flag to track whether PageFactory has been initialized for this instance. */
    private boolean pageFactoryInitialized = false;

    /**
     * Lazily initialize PageFactory elements on first driver access.
     * NOT in @PostConstruct — that would create a browser during bean creation
     * (before any scenario step runs), causing extra browser windows.
     */
    private void ensurePageFactoryInitialized() {
        if (!pageFactoryInitialized) {
            PageFactory.initElements(driverFactory.getDriver(), this);
            pageFactoryInitialized = true;
            log.debug("PageFactory initialized for {}", this.getClass().getSimpleName());
        }
    }

    protected WebDriver getDriver() {
        ensurePageFactoryInitialized();
        return driverFactory.getDriver();
    }

    // ─── Navigation ──────────────────────────────────────────────────

    public void navigateTo(String path) {
        String url = config.getBaseUrl() + path;
        log.info("Navigating to: {}", url);
        getDriver().get(url);
    }

    public void navigateToUrl(String fullUrl) {
        log.info("Navigating to URL: {}", fullUrl);
        getDriver().get(fullUrl);
    }

    public String getCurrentUrl() {
        return getDriver().getCurrentUrl();
    }

    public String getPageTitle() {
        return getDriver().getTitle();
    }

    // ─── Element interactions ────────────────────────────────────────

    protected void click(WebElement element) {
        waitUtils.waitForClickable(toBy(element));
        element.click();
    }

    protected void click(By locator) {
        waitUtils.waitForClickable(locator).click();
    }

    protected void type(WebElement element, String text) {
        waitUtils.waitForVisible(toBy(element));
        element.clear();
        element.sendKeys(text);
    }

    protected void type(By locator, String text) {
        WebElement element = waitUtils.waitForVisible(locator);
        element.clear();
        element.sendKeys(text);
    }

    protected String getText(WebElement element) {
        return waitUtils.waitForVisible(toBy(element)).getText();
    }

    protected String getText(By locator) {
        return waitUtils.waitForVisible(locator).getText();
    }

    protected boolean isDisplayed(By locator) {
        try {
            return getDriver().findElement(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    protected List<WebElement> findElements(By locator) {
        return getDriver().findElements(locator);
    }

    // ─── JavaScript helpers ──────────────────────────────────────────

    protected void scrollToElement(WebElement element) {
        ((JavascriptExecutor) getDriver())
                .executeScript("arguments[0].scrollIntoView({behavior:'smooth',block:'center'});", element);
    }

    protected void jsClick(WebElement element) {
        ((JavascriptExecutor) getDriver()).executeScript("arguments[0].click();", element);
    }

    protected Object executeJs(String script, Object... args) {
        return ((JavascriptExecutor) getDriver()).executeScript(script, args);
    }

    // ─── Resilient (self-healing) locator methods ─────────────────────
    //
    // These use JSON-defined locators from resources/locators/{page}.json
    // with automatic primary → fallback healing. Use these for elements
    // that are prone to locator changes across releases.

    /**
     * Click using resilient locator (primary → fallback).
     * @param pageName    matches JSON file, e.g. "LoginPage"
     * @param elementName matches element in JSON, e.g. "loginButton"
     */
    protected void resilientClick(String pageName, String elementName) {
        resilientLocator.click(pageName, elementName);
    }

    /**
     * Type text using resilient locator.
     */
    protected void resilientType(String pageName, String elementName, String text) {
        resilientLocator.type(pageName, elementName, text);
    }

    /**
     * Get text using resilient locator.
     */
    protected String resilientGetText(String pageName, String elementName) {
        return resilientLocator.getText(pageName, elementName);
    }

    /**
     * Find element using resilient locator.
     */
    protected WebElement resilientFind(String pageName, String elementName) {
        return resilientLocator.find(pageName, elementName);
    }

    /**
     * Check if element is displayed using resilient locator.
     */
    protected boolean resilientIsDisplayed(String pageName, String elementName) {
        return resilientLocator.isDisplayed(pageName, elementName);
    }

    // ─── Utility ─────────────────────────────────────────────────────

    /**
     * Attempt to derive a By locator from a WebElement (for waits).
     * Falls back to a generic approach when PageFactory proxies are used.
     */
    private By toBy(WebElement element) {
        String description = element.toString();
        if (description.contains("-> id:")) {
            String id = description.substring(description.indexOf("-> id:") + 7).replace("]", "").trim();
            return By.id(id);
        } else if (description.contains("-> css selector:")) {
            String css = description.substring(description.indexOf("-> css selector:") + 17).replace("]", "").trim();
            return By.cssSelector(css);
        } else if (description.contains("-> xpath:")) {
            String xpath = description.substring(description.indexOf("-> xpath:") + 10).replace("]", "").trim();
            return By.xpath(xpath);
        } else if (description.contains("-> name:")) {
            String name = description.substring(description.indexOf("-> name:") + 9).replace("]", "").trim();
            return By.name(name);
        }
        // Fallback: use xpath of the element
        return By.xpath("//*");
    }
}
