package com.automation.locator.extractor;

import com.automation.locator.model.ElementLocators;
import com.automation.locator.model.LocatorEntry;
import com.automation.locator.model.PageLocators;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts all interactable elements from the DOM and generates ranked locator strategies.
 *
 * Confidence Scoring (12 tiers, 1.0 = most stable, 0.2 = fallback):
 *   1.0 — data-testid attribute (explicit test marker)
 *   0.95 — id attribute (should be unique)
 *   0.90 — name attribute on input/select (often unique)
 *   0.85 — aria-label (semantic hint)
 *   0.80 — CSS class combination (may be fragile if page design changes)
 *   0.75 — Direct attribute match (value, placeholder, etc.)
 *   0.70 — Nearest unique parent context + tagName
 *   0.65 — Text content exact match
 *   0.60 — Text content contains
 *   0.50 — Element index in parent
 *   0.30 — Positional XPath (//input[3])
 *   0.20 — Full positional XPath (fallback)
 *
 * Usage:
 *   PageLocators extracted = locatorExtractor.extract(driver, "LoginPage", url);
 *   locatorStore.save(extracted);
 */
@Slf4j
@Component
public class LocatorExtractor {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Extract locators from the current page.
     *
     * @param driver      WebDriver instance
     * @param pageName    Logical page name (e.g., "LoginPage")
     * @param pageUrl     Current page URL
     * @return PageLocators with all elements and their ranked locator strategies
     */
    public PageLocators extract(WebDriver driver, String pageName, String pageUrl) {
        log.info("[LocatorExtractor] Extracting locators for page: {} from URL: {}", pageName, pageUrl);

        PageLocators pageLocators = new PageLocators();
        pageLocators.setPageName(pageName);
        pageLocators.setPageUrl(pageUrl);
        pageLocators.setLastUpdated(LocalDateTime.now().format(TIMESTAMP_FORMAT));

        try {
            // Get all interactable elements from DOM
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elementData = (List<Map<String, Object>>)
                    ((JavascriptExecutor) driver).executeScript(getExtractionScript());

            List<ElementLocators> elements = new ArrayList<>();

            for (Map<String, Object> data : elementData) {
                String elementName = (String) data.get("elementName");
                String tag = (String) data.get("tag");

                // Generate locator strategies for this element
                List<LocatorEntry> allLocators = generateLocatorStrategies(data);

                if (allLocators.isEmpty()) {
                    continue;
                }

                // First is primary, rest are fallbacks
                LocatorEntry primary = allLocators.get(0);
                List<LocatorEntry> fallbacks = allLocators.size() > 1
                        ? allLocators.subList(1, allLocators.size())
                        : new ArrayList<>();

                ElementLocators element = new ElementLocators();
                element.setElementName(elementName);
                element.setTag(tag);
                element.setPrimary(primary);
                element.setFallbacks(fallbacks);
                element.setLastCaptured(LocalDateTime.now().format(TIMESTAMP_FORMAT));

                elements.add(element);
            }

            pageLocators.setElements(elements);
            log.info("[LocatorExtractor] Extracted {} elements for page: {}", elements.size(), pageName);

            return pageLocators;
        } catch (Exception e) {
            log.error("[LocatorExtractor] Extraction failed for page '{}': {}", pageName, e.getMessage(), e);
            pageLocators.setElements(new ArrayList<>());
            return pageLocators;
        }
    }

    /**
     * Generate ranked locator strategies for a single element.
     * Returns list sorted by confidence (highest first).
     */
    private List<LocatorEntry> generateLocatorStrategies(Map<String, Object> elementData) {
        List<LocatorEntry> locators = new ArrayList<>();

        // Extract element attributes
        String id = (String) elementData.get("id");
        String name = (String) elementData.get("name");
        String dataTestId = (String) elementData.get("dataTestId");
        String ariaLabel = (String) elementData.get("ariaLabel");
        String classes = (String) elementData.get("classes");
        String text = (String) elementData.get("text");
        String value = (String) elementData.get("value");
        String placeholder = (String) elementData.get("placeholder");
        Integer indexInParent = ((Number) elementData.get("indexInParent")).intValue();
        String tag = (String) elementData.get("tag");

        // Tier 1.0: data-testid (explicit test marker)
        if (dataTestId != null && !dataTestId.isEmpty()) {
            locators.add(new LocatorEntry(
                    "css",
                    "[data-testid='" + dataTestId + "']",
                    1.0,
                    "Explicit test ID attribute"
            ));
        }

        // Tier 0.95: id attribute
        if (id != null && !id.isEmpty()) {
            locators.add(new LocatorEntry(
                    "id",
                    id,
                    0.95,
                    "Unique ID attribute"
            ));
        }

        // Tier 0.90: name attribute (common for form inputs)
        if (name != null && !name.isEmpty()) {
            locators.add(new LocatorEntry(
                    "name",
                    name,
                    0.90,
                    "Name attribute on form element"
            ));
        }

        // Tier 0.85: aria-label
        if (ariaLabel != null && !ariaLabel.isEmpty()) {
            locators.add(new LocatorEntry(
                    "xpath",
                    "//*[@aria-label='" + ariaLabel + "']",
                    0.85,
                    "Accessibility label"
            ));
        }

        // Tier 0.80: CSS class combination
        if (classes != null && !classes.isEmpty()) {
            String[] classArray = classes.split("\\s+");
            if (classArray.length > 0) {
                String cssSelectorClasses = "." + String.join(".", classArray);
                locators.add(new LocatorEntry(
                        "css",
                        cssSelectorClasses,
                        0.80,
                        "CSS class combination"
                ));
            }
        }

        // Tier 0.75: Direct attribute match (value, placeholder)
        if (value != null && !value.isEmpty() && value.length() < 50) {
            locators.add(new LocatorEntry(
                    "xpath",
                    "//" + tag + "[@value='" + escapeXpath(value) + "']",
                    0.75,
                    "Value attribute match"
            ));
        }

        if (placeholder != null && !placeholder.isEmpty() && placeholder.length() < 50) {
            locators.add(new LocatorEntry(
                    "xpath",
                    "//" + tag + "[@placeholder='" + escapeXpath(placeholder) + "']",
                    0.70,
                    "Placeholder attribute match"
            ));
        }

        // Tier 0.65: Text content exact match
        if (text != null && !text.isEmpty() && text.length() < 100) {
            locators.add(new LocatorEntry(
                    "xpath",
                    "//" + tag + "[text()='" + escapeXpath(text) + "']",
                    0.65,
                    "Exact text content match"
            ));
        }

        // Tier 0.60: Text content contains
        if (text != null && !text.isEmpty() && text.length() < 100) {
            locators.add(new LocatorEntry(
                    "xpath",
                    "//" + tag + "[contains(text(), '" + escapeXpath(text) + "')]",
                    0.60,
                    "Text content contains"
            ));
        }

        // Tier 0.50: Element index in parent
        locators.add(new LocatorEntry(
                "xpath",
                "//" + tag + "[" + (indexInParent + 1) + "]",
                0.50,
                "Element index in parent"
        ));

        // Tier 0.30: Positional XPath with tag
        locators.add(new LocatorEntry(
                "xpath",
                "(//" + tag + ")[" + (indexInParent + 1) + "]",
                0.30,
                "Positional XPath by tag"
        ));

        // Tier 0.2: Full positional XPath (absolute fallback)
        locators.add(new LocatorEntry(
                "xpath",
                getAbsoluteXpath(elementData),
                0.2,
                "Full absolute XPath (fragile)"
        ));

        // Sort by confidence descending
        return locators.stream()
                .sorted(Comparator.comparingDouble(LocatorEntry::getConfidence).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get JavaScript to extract all interactable elements from the DOM.
     */
    private String getExtractionScript() {
        return """
            (function() {
                const INTERACTABLE_TAGS = ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM'];
                const elements = [];

                document.querySelectorAll('*').forEach((el, index) => {
                    // Filter to interactable elements
                    if (!INTERACTABLE_TAGS.includes(el.tagName)) return;
                    if (!el.offsetParent) return; // Hidden element

                    // Generate element name
                    let name = el.id || el.name || el.getAttribute('data-testid') || (el.textContent || '').substring(0, 20) || el.tagName.toLowerCase() + '_' + index;
                    name = name.replace(/[^a-zA-Z0-9_]/g, '_');

                    elements.push({
                        elementName: name,
                        tag: el.tagName.toLowerCase(),
                        id: el.id || '',
                        name: el.name || '',
                        dataTestId: el.getAttribute('data-testid') || '',
                        ariaLabel: el.getAttribute('aria-label') || '',
                        classes: el.className || '',
                        text: el.textContent ? el.textContent.trim().substring(0, 100) : '',
                        value: el.value || '',
                        placeholder: el.getAttribute('placeholder') || '',
                        indexInParent: Array.from(el.parentNode.children).indexOf(el)
                    });
                });

                return elements;
            })();
            """;
    }

    /**
     * Build full absolute XPath for an element (fragile but guaranteed to work).
     */
    private String getAbsoluteXpath(Map<String, Object> elementData) {
        String tag = (String) elementData.get("tag");
        Integer indexInParent = ((Number) elementData.get("indexInParent")).intValue();
        return "//" + tag + "[" + (indexInParent + 1) + "]";
    }

    /**
     * Escape special characters in XPath string values.
     */
    private String escapeXpath(String value) {
        return value.replace("'", "\\'");
    }
}
