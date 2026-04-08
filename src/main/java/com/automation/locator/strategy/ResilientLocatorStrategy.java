package com.automation.locator.strategy;

import com.automation.driver.DriverFactory;
import com.automation.locator.extractor.LocatorExtractor;
import com.automation.locator.model.ElementLocators;
import com.automation.locator.model.LocatorEntry;
import com.automation.locator.model.PageLocators;
import com.automation.locator.store.LocatorStore;
import com.automation.utils.WaitUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Self-healing locator strategy: tries primary locator, falls back to ranked alternatives.
 *
 * Core Methods:
 *   find(pageName, elementName) → finds element, tries primary first, falls back as needed
 *   findVisible(pageName, elementName) → finds visible element
 *   findClickable(pageName, elementName) → finds clickable element
 *   findAll(pageName, elementName) → finds all matching elements
 *
 * High-Level Actions:
 *   click(pageName, elementName)
 *   type(pageName, elementName, text)
 *   getText(pageName, elementName)
 *   isDisplayed(pageName, elementName)
 *
 * Lazy Extraction:
 *   If locators not found in JSON, automatically extracts them on first use.
 */
@Slf4j
@Component
public class ResilientLocatorStrategy {

    @Autowired private DriverFactory driverFactory;
    @Autowired private LocatorStore locatorStore;
    @Autowired private LocatorExtractor locatorExtractor;
    @Autowired private WaitUtils waitUtils;

    // ─── Core Locator Discovery ───────────────────────────────────────

    /**
     * Find element with self-healing: tries primary locator, falls back to alternatives.
     * Logs warnings when fallback is used.
     *
     * @throws NoSuchElementException if element not found after trying all locators
     */
    public WebElement find(String pageName, String elementName) {
        WebDriver driver = driverFactory.getDriver();
        ElementLocators element = getElementLocators(pageName, elementName);

        if (element == null) {
            throw new NoSuchElementException("Element '" + elementName + "' not found in locator store for page '" + pageName + "'");
        }

        // Try primary locator
        LocatorEntry primary = element.getPrimary();
        try {
            By locator = toBy(primary);
            WebElement webElement = driver.findElement(locator);
            log.debug("[ResilientLocator] Element '{}' found with PRIMARY locator ({})",
                    elementName, primary.getType());
            return webElement;
        } catch (Exception e) {
            log.warn("[ResilientLocator] PRIMARY locator failed for '{}' ({}), trying fallbacks",
                    elementName, primary.getType());
        }

        // Try fallback locators in order
        if (element.getFallbacks() != null && !element.getFallbacks().isEmpty()) {
            for (LocatorEntry fallback : element.getFallbacks()) {
                try {
                    By locator = toBy(fallback);
                    WebElement webElement = driver.findElement(locator);
                    log.warn("[ResilientLocator] Element '{}' found with FALLBACK locator ({}), confidence: {}",
                            elementName, fallback.getType(), fallback.getConfidence());
                    return webElement;
                } catch (Exception e) {
                    // Continue to next fallback
                }
            }
        }

        // No locator worked
        throw new NoSuchElementException(
                "Element '" + elementName + "' not found with any locator strategy for page '" + pageName + "'");
    }

    /**
     * Find element that is visible (with wait).
     */
    public WebElement findVisible(String pageName, String elementName) {
        WebElement element = find(pageName, elementName);
        return waitUtils.waitForVisible(toBy(element));
    }

    /**
     * Find element that is clickable (with wait).
     */
    public WebElement findClickable(String pageName, String elementName) {
        WebElement element = find(pageName, elementName);
        return waitUtils.waitForClickable(toBy(element));
    }

    /**
     * Find all elements matching the locators.
     */
    public List<WebElement> findAll(String pageName, String elementName) {
        WebDriver driver = driverFactory.getDriver();
        ElementLocators element = getElementLocators(pageName, elementName);

        if (element == null) {
            throw new NoSuchElementException("Element '" + elementName + "' not found in locator store for page '" + pageName + "'");
        }

        try {
            By locator = toBy(element.getPrimary());
            return driver.findElements(locator);
        } catch (Exception e) {
            log.warn("[ResilientLocator] findAll failed for primary locator of '{}', trying fallbacks", elementName);
            if (element.getFallbacks() != null && !element.getFallbacks().isEmpty()) {
                for (LocatorEntry fallback : element.getFallbacks()) {
                    try {
                        By locator = toBy(fallback);
                        return driver.findElements(locator);
                    } catch (Exception ex) {
                        // Continue to next fallback
                    }
                }
            }
        }

        return List.of();
    }

    // ─── High-Level Actions ───────────────────────────────────────────

    /**
     * Click element.
     */
    public void click(String pageName, String elementName) {
        log.info("[ResilientLocator] Clicking element '{}' on page '{}'", elementName, pageName);
        WebElement element = findClickable(pageName, elementName);
        element.click();
    }

    /**
     * Type text into element (clears first).
     */
    public void type(String pageName, String elementName, String text) {
        log.info("[ResilientLocator] Typing into element '{}' on page '{}'", elementName, pageName);
        WebElement element = findVisible(pageName, elementName);
        element.clear();
        element.sendKeys(text);
    }

    /**
     * Get text content of element.
     */
    public String getText(String pageName, String elementName) {
        WebElement element = findVisible(pageName, elementName);
        String text = element.getText();
        log.debug("[ResilientLocator] Got text from '{}': {}", elementName, text);
        return text;
    }

    /**
     * Check if element is displayed.
     */
    public boolean isDisplayed(String pageName, String elementName) {
        try {
            WebElement element = find(pageName, elementName);
            return element.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Locator Loading & Caching ────────────────────────────────────

    /**
     * Get element locators from store, with lazy extraction if missing.
     * This is where self-healing auto-extraction happens on first use.
     *
     * @return ElementLocators or null if not found after extraction attempt
     */
    private ElementLocators getElementLocators(String pageName, String elementName) {
        PageLocators pageLocators = locatorStore.load(pageName);

        // If JSON doesn't exist or is empty, auto-extract
        if (pageLocators == null || pageLocators.getElements() == null || pageLocators.getElements().isEmpty()) {
            log.info("[ResilientLocator] No locators found for page '{}', auto-extracting...", pageName);
            WebDriver driver = driverFactory.getDriver();
            String pageUrl = driver.getCurrentUrl();
            pageLocators = locatorExtractor.extract(driver, pageName, pageUrl);
            locatorStore.save(pageLocators);
        }

        // Find matching element
        if (pageLocators != null && pageLocators.getElements() != null) {
            for (ElementLocators element : pageLocators.getElements()) {
                if (element.getElementName().equals(elementName)) {
                    return element;
                }
            }
        }

        return null;
    }

    // ─── Utility ──────────────────────────────────────────────────────

    /**
     * Convert LocatorEntry to Selenium By locator.
     */
    private By toBy(LocatorEntry entry) {
        return switch (entry.getType().toLowerCase()) {
            case "id" -> By.id(entry.getValue());
            case "css", "css selector" -> By.cssSelector(entry.getValue());
            case "xpath" -> By.xpath(entry.getValue());
            case "name" -> By.name(entry.getValue());
            case "class name" -> By.className(entry.getValue());
            case "link text" -> By.linkText(entry.getValue());
            case "partial link text" -> By.partialLinkText(entry.getValue());
            case "tag name" -> By.tagName(entry.getValue());
            default -> {
                log.warn("[ResilientLocator] Unknown locator type '{}', falling back to xpath", entry.getType());
                yield By.xpath(entry.getValue());
            }
        };
    }

    /**
     * Extract By locator from WebElement (works with Selenium proxies).
     * Falls back to generic approach when exact locator can't be derived.
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
        // Fallback to find parent in DOM
        return By.xpath("//*");
    }
}
