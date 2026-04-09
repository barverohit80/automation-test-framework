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
            Object result = ((JavascriptExecutor) driver).executeScript(getExtractionScript());

            log.info("[LocatorExtractor] JavaScript returned: {} (type: {})",
                    result, result != null ? result.getClass().getName() : "null");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elementData = result instanceof List
                    ? (List<Map<String, Object>>) result
                    : new ArrayList<>();

            log.debug("[LocatorExtractor] Extracted {} elements from DOM",
                    elementData.size());

            if (elementData == null || elementData.isEmpty()) {
                log.warn("[LocatorExtractor] ⚠ No interactable elements found on page '{}'. " +
                        "Check if page is fully loaded or if elements are hidden/invisible.", pageName);
                pageLocators.setElements(new ArrayList<>());
                return pageLocators;
            }

            List<ElementLocators> elements = new ArrayList<>();

            for (Map<String, Object> data : elementData) {
                String elementName = (String) data.get("elementName");
                String tag = (String) data.get("tag");

                // Filter out elements based on visibility and interactivity
                if (!shouldIncludeElement(data)) {
                    continue;
                }

                // Generate locator strategies for this element
                List<LocatorEntry> allLocators = generateLocatorStrategies(data);

                if (allLocators.isEmpty()) {
                    log.debug("[LocatorExtractor] Skipped element '{}' - no locators generated", elementName);
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
            log.info("[LocatorExtractor] ✓ Extracted {} elements for page: {}", elements.size(), pageName);

            if (elements.isEmpty()) {
                log.warn("[LocatorExtractor] ⚠ Extracted 0 elements. Check if page structure matches expected selectors.");
            }

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
     * Determine if an element should be included based on visibility and interactivity.
     * Mirrors the filtering logic from DebugLocatorSteps.
     */
    private boolean shouldIncludeElement(Map<String, Object> elementData) {
        String tag = (String) elementData.get("tag");
        String display = (String) elementData.get("display");
        String visibility = (String) elementData.get("visibility");
        String opacity = (String) elementData.get("opacity");
        String ariaHidden = (String) elementData.get("ariaHidden");
        String role = (String) elementData.get("role");
        Boolean onclick = (Boolean) elementData.get("onclick");
        String id = (String) elementData.get("id");
        String name = (String) elementData.get("name");
        String text = (String) elementData.get("text");

        // Skip if display:none
        if ("none".equalsIgnoreCase(display)) {
            return false;
        }

        // Skip if visibility:hidden (only if explicitly set)
        if ("hidden".equalsIgnoreCase(visibility)) {
            return false;
        }

        // Skip if opacity:0 (only if explicitly set)
        if ("0".equals(opacity)) {
            return false;
        }

        // Skip if aria-hidden
        if ("true".equals(ariaHidden)) {
            return false;
        }

        // For DIV/SPAN, check if interactive
        if ("div".equalsIgnoreCase(tag) || "span".equalsIgnoreCase(tag)) {
            boolean hasButtonRole = "button".equalsIgnoreCase(role);
            boolean hasOnclick = onclick != null && onclick;
            if (!hasButtonRole && !hasOnclick) {
                return false; // Not interactive
            }
        }

        // Check if has identifying info
        boolean hasId = id != null && !id.isEmpty();
        boolean hasName = name != null && !name.isEmpty();
        boolean hasText = text != null && !text.trim().isEmpty();
        boolean hasRole = role != null && !role.isEmpty();

        if (!hasId && !hasName && !hasText && !hasRole) {
            return false; // No identifying info
        }

        return true;
    }

    /**
     * Get JavaScript to extract all interactable elements from the DOM.
     * NO IIFE - returns directly like Selenium/WebDriver expects
     */
    private String getExtractionScript() {
        return """
            const elements = [];
            let elemCount = 0;
            const tags = ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM', 'DIV', 'SPAN'];

            document.querySelectorAll('*').forEach((el) => {
                if (!tags.includes(el.tagName)) return;

                // Build full element object
                let count = ++elemCount;
                let name = el.id || el.name || el.getAttribute('data-testid')
                    || el.getAttribute('aria-label') || el.getAttribute('placeholder')
                    || el.getAttribute('value') || (el.textContent || '').trim().substring(0, 30)
                    || el.tagName.toLowerCase() + '_' + count;

                // Sanitize
                name = name.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 50);

                // Calculate indexInParent safely
                let indexInParent = -1;
                try {
                    if (el.parentNode) {
                        indexInParent = Array.from(el.parentNode.children).indexOf(el);
                    }
                } catch (e) {
                    indexInParent = -1;
                }

                elements.push({
                    elementName: name,
                    tag: el.tagName.toLowerCase(),
                    id: el.id || '',
                    name: el.name || '',
                    dataTestId: el.getAttribute('data-testid') || '',
                    ariaLabel: el.getAttribute('aria-label') || '',
                    classes: el.className || '',
                    text: (el.textContent || '').trim().substring(0, 100),
                    value: el.value || el.getAttribute('value') || '',
                    placeholder: el.getAttribute('placeholder') || '',
                    display: window.getComputedStyle(el).display,
                    visibility: window.getComputedStyle(el).visibility,
                    opacity: window.getComputedStyle(el).opacity,
                    ariaHidden: el.getAttribute('aria-hidden'),
                    role: el.getAttribute('role'),
                    onclick: !!el.getAttribute('onclick'),
                    indexInParent: indexInParent
                });
            });

            return elements;
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
