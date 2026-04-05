package com.automation.locator.extractor;

import com.automation.driver.DriverFactory;
import com.automation.locator.model.ElementLocators;
import com.automation.locator.model.LocatorEntry;
import com.automation.locator.model.PageLocators;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts ALL possible locators (primary + fallbacks) for every interactable element
 * on the current page. Each element gets a ranked list of locators scored by stability.
 *
 * Scoring rules (higher = more stable):
 *   1.0  data-testid          — purpose-built for testing, never changes
 *   0.95 data-qa              — purpose-built for QA
 *   0.9  id                   — stable unless auto-generated
 *   0.85 aria-label           — accessibility contract, rarely changed
 *   0.8  name                 — form attribute, fairly stable
 *   0.7  placeholder          — changes with i18n, but unique
 *   0.6  css class combo      — stable classes only (no hashes/numbers)
 *   0.5  link text            — visible text, changes with copy updates
 *   0.4  text-based xpath     — fragile to text changes
 *   0.3  structural css       — parent > child relationships
 *   0.2  positional xpath     — most fragile, last resort
 */
@Slf4j
@Component
public class LocatorExtractor {

    @Autowired
    private DriverFactory driverFactory;

    /**
     * JavaScript that extracts every interactable element and ALL its locator-worthy attributes.
     * Returns a List of Maps with all attributes needed to build locator strategies.
     */
    private static final String EXTRACT_ALL_LOCATORS_JS = """
        const selectors = 'input,button,select,textarea,a,[role="button"],[role="link"],' +
                          '[role="tab"],[role="menuitem"],[data-testid],[data-qa],h1,h2,h3,label';
        const results = [];
        const seen = new Set();
        let counter = 0;

        document.querySelectorAll(selectors).forEach(el => {
            if (seen.has(el)) return;
            seen.add(el);

            const rect = el.getBoundingClientRect();
            if (rect.width === 0 && rect.height === 0) return;

            const tag = el.tagName.toLowerCase();
            const text = (el.innerText || el.textContent || '').trim().substring(0, 80);
            const value = el.value || '';

            // Build a structural CSS path (parent > child)
            let structuralCss = '';
            try {
                let path = [];
                let current = el;
                for (let i = 0; i < 3 && current && current !== document.body; i++) {
                    let seg = current.tagName.toLowerCase();
                    if (current.id) { seg += '#' + current.id; path.unshift(seg); break; }
                    const parent = current.parentElement;
                    if (parent) {
                        const siblings = Array.from(parent.children).filter(c => c.tagName === current.tagName);
                        if (siblings.length > 1) {
                            seg += ':nth-of-type(' + (siblings.indexOf(current) + 1) + ')';
                        }
                    }
                    path.unshift(seg);
                    current = parent;
                }
                structuralCss = path.join(' > ');
            } catch(e) {}

            // Stable classes only (no hashes, no numbers, short)
            const stableClasses = Array.from(el.classList)
                .filter(c => !/[0-9]/.test(c) && c.length < 30 && c.length > 1);

            results.push({
                index:         counter++,
                tag:           tag,
                id:            el.id || null,
                name:          el.name || null,
                type:          el.type || null,
                text:          text || null,
                value:         value || null,
                placeholder:   el.placeholder || null,
                ariaLabel:     el.getAttribute('aria-label'),
                ariaRole:      el.getAttribute('role'),
                dataTestId:    el.getAttribute('data-testid'),
                dataQa:        el.getAttribute('data-qa'),
                href:          el.href || null,
                title:         el.title || null,
                forAttr:       el.getAttribute('for'),
                stableClasses: stableClasses,
                structuralCss: structuralCss,
                isRequired:    el.required || false,
                isDisabled:    el.disabled || false
            });
        });
        return results;
        """;

    /**
     * Extract all locators for all interactable elements on the current page.
     *
     * @param pageName logical page name for the output JSON
     * @return PageLocators with primary + fallback locators for each element
     */
    public PageLocators extractAll(String pageName) {
        return extractAll(driverFactory.getDriver(), pageName);
    }

    /**
     * Extract all locators for all interactable elements on the given page.
     */
    @SuppressWarnings("unchecked")
    public PageLocators extractAll(WebDriver driver, String pageName) {
        log.info("[LocatorExtractor] Extracting locators from page: {}", pageName);

        JavascriptExecutor js = (JavascriptExecutor) driver;
        List<Map<String, Object>> rawElements =
                (List<Map<String, Object>>) js.executeScript(EXTRACT_ALL_LOCATORS_JS);

        String pageUrl = driver.getCurrentUrl();
        String timestamp = LocalDateTime.now().toString();

        List<ElementLocators> elements = rawElements.stream()
                .map(raw -> buildElementLocators(raw, timestamp))
                .collect(Collectors.toList());

        log.info("[LocatorExtractor] Extracted locators for {} elements on {}", elements.size(), pageName);

        PageLocators page = new PageLocators();
        page.setPageName(pageName);
        page.setPageUrl(pageUrl);
        page.setElements(elements);
        return page;
    }

    /**
     * Builds an ElementLocators object from raw JS-extracted attributes.
     * Generates all possible locators, scores them, picks the best as primary,
     * and ranks the rest as fallbacks.
     */
    private ElementLocators buildElementLocators(Map<String, Object> raw, String timestamp) {
        String tag = str(raw, "tag");
        String id = str(raw, "id");
        String name = str(raw, "name");
        String type = str(raw, "type");
        String text = str(raw, "text");
        String placeholder = str(raw, "placeholder");
        String ariaLabel = str(raw, "ariaLabel");
        String dataTestId = str(raw, "dataTestId");
        String dataQa = str(raw, "dataQa");
        String href = str(raw, "href");
        String title = str(raw, "title");
        String structuralCss = str(raw, "structuralCss");
        @SuppressWarnings("unchecked")
        List<String> stableClasses = (List<String>) raw.getOrDefault("stableClasses", List.of());

        // Generate all possible locators with confidence scores
        List<LocatorEntry> candidates = new ArrayList<>();

        // 1. data-testid (1.0) — gold standard
        if (dataTestId != null && !dataTestId.isBlank()) {
            candidates.add(new LocatorEntry("css", "[data-testid='" + dataTestId + "']",
                    1.0, "data-testid attribute"));
        }

        // 2. data-qa (0.95)
        if (dataQa != null && !dataQa.isBlank()) {
            candidates.add(new LocatorEntry("css", "[data-qa='" + dataQa + "']",
                    0.95, "data-qa attribute"));
        }

        // 3. id (0.9) — skip if looks auto-generated
        if (id != null && !id.isBlank() && !looksAutoGenerated(id)) {
            candidates.add(new LocatorEntry("id", id,
                    0.9, "stable id attribute"));
        }

        // 4. aria-label (0.85)
        if (ariaLabel != null && !ariaLabel.isBlank()) {
            candidates.add(new LocatorEntry("css", tag + "[aria-label='" + escCss(ariaLabel) + "']",
                    0.85, "aria-label attribute"));
        }

        // 5. name (0.8)
        if (name != null && !name.isBlank()) {
            candidates.add(new LocatorEntry("name", name,
                    0.8, "name attribute"));
        }

        // 6. placeholder (0.7)
        if (placeholder != null && !placeholder.isBlank()) {
            candidates.add(new LocatorEntry("css", tag + "[placeholder='" + escCss(placeholder) + "']",
                    0.7, "placeholder attribute"));
        }

        // 7. title attribute (0.7)
        if (title != null && !title.isBlank()) {
            candidates.add(new LocatorEntry("css", tag + "[title='" + escCss(title) + "']",
                    0.7, "title attribute"));
        }

        // 8. css class combo (0.6)
        if (stableClasses != null && !stableClasses.isEmpty()) {
            String classSelector = tag + "." + String.join(".", stableClasses);
            candidates.add(new LocatorEntry("css", classSelector,
                    0.6, "stable CSS classes"));
        }

        // 9. link text (0.5) — for <a> tags
        if ("a".equals(tag) && text != null && !text.isBlank() && text.length() < 50) {
            candidates.add(new LocatorEntry("linkText", text,
                    0.5, "link text"));
        }

        // 10. text-based xpath (0.4) — for buttons and labels
        if (text != null && !text.isBlank() && text.length() < 40
                && ("button".equals(tag) || "label".equals(tag) || "h1".equals(tag)
                || "h2".equals(tag) || "h3".equals(tag))) {
            candidates.add(new LocatorEntry("xpath",
                    "//" + tag + "[normalize-space()='" + escXpath(text) + "']",
                    0.4, "text-based xpath"));
        }

        // 11. structural css (0.3)
        if (structuralCss != null && !structuralCss.isBlank()) {
            candidates.add(new LocatorEntry("css", structuralCss,
                    0.3, "structural CSS path"));
        }

        // 12. type-based fallback (0.2) — e.g. input[type='password'] on page with one password
        if (type != null && !type.isBlank() && !"text".equals(type)) {
            candidates.add(new LocatorEntry("css", tag + "[type='" + type + "']",
                    0.2, "input type fallback"));
        }

        // Sort by confidence descending
        candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));

        // Derive element name
        String elementName = deriveElementName(tag, id, name, dataTestId, dataQa, text, placeholder, type);

        ElementLocators el = new ElementLocators();
        el.setElementName(elementName);
        el.setTag(tag);
        el.setLastCaptured(timestamp);

        if (!candidates.isEmpty()) {
            el.setPrimary(candidates.get(0));
            el.setFallbacks(candidates.size() > 1 ? candidates.subList(1, candidates.size()) : List.of());
        } else {
            // Absolute last resort
            el.setPrimary(new LocatorEntry("css", tag, 0.1, "tag-only fallback"));
            el.setFallbacks(List.of());
        }

        return el;
    }

    /**
     * Derives a human-readable element name from its attributes.
     */
    private String deriveElementName(String tag, String id, String name,
                                     String dataTestId, String dataQa,
                                     String text, String placeholder, String type) {
        if (dataTestId != null && !dataTestId.isBlank()) return toCamelCase(dataTestId);
        if (dataQa != null && !dataQa.isBlank()) return toCamelCase(dataQa);
        if (id != null && !id.isBlank()) return toCamelCase(id);
        if (name != null && !name.isBlank()) return toCamelCase(name);
        if (text != null && !text.isBlank() && text.length() < 30) return toCamelCase(text) + capitalize(tag);
        if (placeholder != null && !placeholder.isBlank()) return toCamelCase(placeholder) + capitalize(tag);
        if (type != null && !type.isBlank()) return type + capitalize(tag);
        return tag;
    }

    private String toCamelCase(String s) {
        String clean = s.replaceAll("[^a-zA-Z0-9\\s_-]", "");
        String[] parts = clean.split("[\\s_-]+");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Checks if an ID looks auto-generated (contains hashes, UUIDs, or long number sequences) */
    private boolean looksAutoGenerated(String id) {
        return id.matches(".*[0-9a-f]{8,}.*")     // hex hash
                || id.matches(".*\\d{5,}.*")        // 5+ digit sequence
                || id.contains("ember")             // ember.js auto-ids
                || id.contains("react")             // react auto-ids
                || id.startsWith(":");              // CSS-generated
    }

    private String escCss(String val) {
        return val.replace("'", "\\'");
    }

    private String escXpath(String val) {
        return val.replace("'", "\\'");
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
