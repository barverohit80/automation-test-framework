package com.automation.locator.strategy;

import com.automation.locator.extractor.LocatorExtractor;
import com.automation.locator.model.ElementLocators;
import com.automation.locator.model.LocatorEntry;
import com.automation.locator.model.PageLocators;
import com.automation.locator.store.LocatorStore;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-healing element locator strategy.
 *
 * Tries the primary locator first. If it fails (NoSuchElement, StaleElement),
 * iterates through fallback locators in confidence order until one works.
 *
 * When a fallback succeeds, it logs a warning so you know the primary locator
 * needs updating — the test still passes instead of failing flakily.
 *
 * Usage in page objects:
 *
 *   @Autowired private ResilientLocatorStrategy locator;
 *
 *   public void enterUsername(String text) {
 *       locator.type("LoginPage", "usernameInput", text);
 *   }
 *
 *   public void clickLogin() {
 *       locator.click("LoginPage", "loginButton");
 *   }
 *
 *   public String getErrorMessage() {
 *       return locator.getText("LoginPage", "outputMessage");
 *   }
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class ResilientLocatorStrategy {

    @Autowired
    private LocatorStore locatorStore;

    @Autowired
    private LocatorExtractor locatorExtractor;

    @Autowired
    private com.automation.driver.DriverFactory driverFactory;

    @Autowired
    private com.automation.config.EnvironmentConfig config;

    // ─── Core: find element with fallback ─────────────────────────────

    /**
     * Find an element using primary locator first, then fallbacks.
     *
     * @param pageName    matches the JSON file name, e.g. "LoginPage"
     * @param elementName matches the elementName in JSON, e.g. "usernameInput"
     * @return the found WebElement
     * @throws NoSuchElementException if ALL locators fail
     */
    public WebElement find(String pageName, String elementName) {
        ElementLocators el = getElementLocators(pageName, elementName);
        return findWithFallback(el);
    }

    /**
     * Find an element and wait for it to be visible.
     */
    public WebElement findVisible(String pageName, String elementName) {
        ElementLocators el = getElementLocators(pageName, elementName);
        return findVisibleWithFallback(el);
    }

    /**
     * Find an element and wait for it to be clickable.
     */
    public WebElement findClickable(String pageName, String elementName) {
        ElementLocators el = getElementLocators(pageName, elementName);
        return findClickableWithFallback(el);
    }

    /**
     * Find multiple elements matching the locator.
     */
    public List<WebElement> findAll(String pageName, String elementName) {
        ElementLocators el = getElementLocators(pageName, elementName);
        return findAllWithFallback(el);
    }

    // ─── High-level actions ───────────────────────────────────────────

    /**
     * Click an element with self-healing locator.
     */
    public void click(String pageName, String elementName) {
        WebElement element = findClickable(pageName, elementName);
        element.click();
        log.debug("[Resilient] Clicked: {}.{}", pageName, elementName);
    }

    /**
     * Type text into an element with self-healing locator.
     */
    public void type(String pageName, String elementName, String text) {
        WebElement element = findVisible(pageName, elementName);
        element.clear();
        element.sendKeys(text);
        log.debug("[Resilient] Typed into: {}.{}", pageName, elementName);
    }

    /**
     * Get text from an element with self-healing locator.
     */
    public String getText(String pageName, String elementName) {
        WebElement element = findVisible(pageName, elementName);
        return element.getText();
    }

    /**
     * Check if an element is displayed (returns false instead of throwing).
     */
    public boolean isDisplayed(String pageName, String elementName) {
        try {
            WebElement element = find(pageName, elementName);
            return element.isDisplayed();
        } catch (NoSuchElementException | TimeoutException e) {
            return false;
        }
    }

    // ─── Internal fallback logic ──────────────────────────────────────

    private WebElement findWithFallback(ElementLocators el) {
        List<LocatorEntry> allLocators = getAllLocators(el);
        WebDriver driver = driverFactory.getDriver();
        List<String> tried = new ArrayList<>();

        for (int i = 0; i < allLocators.size(); i++) {
            LocatorEntry entry = allLocators.get(i);
            By by = toBy(entry);
            tried.add(entry.getType() + "=" + entry.getValue());

            try {
                WebElement found = driver.findElement(by);
                if (found.isDisplayed() || found.isEnabled()) {
                    if (i > 0) {
                        logFallbackUsed(el.getElementName(), el.getPrimary(), entry, i);
                    }
                    return found;
                }
            } catch (NoSuchElementException | StaleElementReferenceException ignored) {
                // Try next locator
            }
        }

        throw new NoSuchElementException(
                String.format("[Resilient] All locators failed for '%s'. Tried: %s",
                        el.getElementName(), tried));
    }

    private WebElement findVisibleWithFallback(ElementLocators el) {
        List<LocatorEntry> allLocators = getAllLocators(el);
        int timeout = config.getBrowser().getExplicitWaitSeconds();
        // Use shorter timeout per locator attempt to avoid long total wait
        int perLocatorTimeout = Math.max(2, timeout / allLocators.size());
        WebDriver driver = driverFactory.getDriver();

        for (int i = 0; i < allLocators.size(); i++) {
            LocatorEntry entry = allLocators.get(i);
            By by = toBy(entry);

            try {
                WebElement found = new WebDriverWait(driver, Duration.ofSeconds(perLocatorTimeout))
                        .until(ExpectedConditions.visibilityOfElementLocated(by));
                if (i > 0) {
                    logFallbackUsed(el.getElementName(), el.getPrimary(), entry, i);
                }
                return found;
            } catch (TimeoutException | NoSuchElementException ignored) {
                // Try next locator
            }
        }

        // Last attempt with full timeout on primary
        By primaryBy = toBy(el.getPrimary());
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOfElementLocated(primaryBy));
    }

    private WebElement findClickableWithFallback(ElementLocators el) {
        List<LocatorEntry> allLocators = getAllLocators(el);
        int timeout = config.getBrowser().getExplicitWaitSeconds();
        int perLocatorTimeout = Math.max(2, timeout / allLocators.size());
        WebDriver driver = driverFactory.getDriver();

        for (int i = 0; i < allLocators.size(); i++) {
            LocatorEntry entry = allLocators.get(i);
            By by = toBy(entry);

            try {
                WebElement found = new WebDriverWait(driver, Duration.ofSeconds(perLocatorTimeout))
                        .until(ExpectedConditions.elementToBeClickable(by));
                if (i > 0) {
                    logFallbackUsed(el.getElementName(), el.getPrimary(), entry, i);
                }
                return found;
            } catch (TimeoutException | NoSuchElementException ignored) {
                // Try next locator
            }
        }

        By primaryBy = toBy(el.getPrimary());
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(primaryBy));
    }

    private List<WebElement> findAllWithFallback(ElementLocators el) {
        List<LocatorEntry> allLocators = getAllLocators(el);
        WebDriver driver = driverFactory.getDriver();

        for (int i = 0; i < allLocators.size(); i++) {
            LocatorEntry entry = allLocators.get(i);
            By by = toBy(entry);

            try {
                List<WebElement> found = driver.findElements(by);
                if (!found.isEmpty()) {
                    if (i > 0) {
                        logFallbackUsed(el.getElementName(), el.getPrimary(), entry, i);
                    }
                    return found;
                }
            } catch (Exception ignored) {
                // Try next locator
            }
        }

        return List.of();
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private ElementLocators getElementLocators(String pageName, String elementName) {
        PageLocators page = locatorStore.load(pageName);
        if (page == null || page.getElements() == null || page.getElements().isEmpty()) {
            log.info("[Resilient] No locators found for '{}' — extracting from current page", pageName);
            try {
                page = locatorExtractor.extractAll(driverFactory.getDriver(), pageName);
                locatorStore.save(page);
                log.info("[Resilient] Auto-extracted and saved locators for '{}' ({} elements)",
                        pageName, page.getElements().size());
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Resilient] No locator file for page '" + pageName
                                + "' and auto-extraction failed: " + e.getMessage(), e);
            }
        }

        return page.getElements().stream()
                .filter(e -> e.getElementName().equals(elementName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "[Resilient] Element '" + elementName + "' not found in " + pageName + ".json"));
    }

    private List<LocatorEntry> getAllLocators(ElementLocators el) {
        List<LocatorEntry> all = new ArrayList<>();
        all.add(el.getPrimary());
        if (el.getFallbacks() != null) {
            all.addAll(el.getFallbacks());
        }
        return all;
    }

    /**
     * Converts a LocatorEntry to a Selenium By object.
     */
    private By toBy(LocatorEntry entry) {
        return switch (entry.getType().toLowerCase()) {
            case "id" -> By.id(entry.getValue());
            case "css" -> By.cssSelector(entry.getValue());
            case "xpath" -> By.xpath(entry.getValue());
            case "name" -> By.name(entry.getValue());
            case "linktext", "linkText" -> By.linkText(entry.getValue());
            case "classname", "className" -> By.className(entry.getValue());
            default -> By.cssSelector(entry.getValue());
        };
    }

    private void logFallbackUsed(String elementName, LocatorEntry primary, LocatorEntry used, int fallbackIndex) {
        log.warn("[Resilient] PRIMARY LOCATOR FAILED for '{}': {}={}. "
                        + "HEALED using fallback #{}: {}={} (confidence={})."
                        + " Update the primary locator to prevent future flakiness.",
                elementName,
                primary.getType(), primary.getValue(),
                fallbackIndex,
                used.getType(), used.getValue(), used.getConfidence());
    }
}
