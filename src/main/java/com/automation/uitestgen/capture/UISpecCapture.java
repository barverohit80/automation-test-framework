package com.automation.uitestgen.capture;

import com.automation.driver.DriverFactory;
import com.automation.uitestgen.model.ElementInfo;
import com.automation.uitestgen.model.PageSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Captures everything Claude needs to generate a Page Object + feature file:
 *   1. Filtered HTML  (forms, buttons, inputs, links — no scripts/styles)
 *   2. Accessibility tree  (ARIA roles, labels, IDs, data-testid)
 *   3. Interactable element list  (structured ElementInfo objects)
 *   4. Screenshot as Base64  (for vision-mode fallback)
 */
@Slf4j
@Component
public class UISpecCapture {

    @Autowired
    private DriverFactory driverFactory;

    // JS that walks the DOM and returns only interactive/semantic elements
    private static final String EXTRACT_ELEMENTS_JS = """
        const tags = ['input','button','select','textarea','a','[role]',
                      '[data-testid]','[data-qa]','[aria-label]','h1','h2','h3',
                      'label','form','table','th','td'];
        const results = [];
        const seen = new Set();
        tags.forEach(selector => {
            document.querySelectorAll(selector).forEach(el => {
                if (seen.has(el)) return;
                seen.add(el);
                const rect = el.getBoundingClientRect();
                if (rect.width === 0 && rect.height === 0) return;
                results.push({
                    tag:          el.tagName.toLowerCase(),
                    id:           el.id || null,
                    name:         el.name || null,
                    type:         el.type || null,
                    text:         (el.innerText || el.value || '').trim().substring(0,80),
                    placeholder:  el.placeholder || null,
                    ariaLabel:    el.getAttribute('aria-label'),
                    ariaRole:     el.getAttribute('role'),
                    dataTestId:   el.getAttribute('data-testid'),
                    dataQa:       el.getAttribute('data-qa'),
                    classes:      Array.from(el.classList)
                                       .filter(c => !/[0-9]/.test(c) && c.length < 30),
                    href:         el.href || null,
                    isRequired:   el.required || false,
                    isDisabled:   el.disabled || false,
                    outerHTML:    el.outerHTML.substring(0, 300)
                });
            });
        });
        return results;
        """;

    // JS that extracts the accessibility tree as a readable text block
    private static final String EXTRACT_A11Y_JS = """
        function getA11yTree(el, depth) {
            if (depth > 6) return '';
            const role  = el.getAttribute('role') || el.tagName.toLowerCase();
            const label = el.getAttribute('aria-label')
                       || el.getAttribute('aria-labelledby')
                       || el.placeholder
                       || el.innerText?.trim().substring(0,60)
                       || '';
            const testId = el.getAttribute('data-testid') || '';
            const id     = el.id ? '#' + el.id : '';
            const indent = '  '.repeat(depth);
            let line = '';
            if (['button','input','select','textarea','a','h1','h2','h3',
                 'label','form','table'].includes(role) || testId || id) {
                line = indent + role
                     + (id     ? ' ' + id       : '')
                     + (testId ? ' [' + testId + ']' : '')
                     + (label  ? ' "' + label + '"'  : '') + '\\n';
            }
            return line + Array.from(el.children)
                               .map(c => getA11yTree(c, depth + 1))
                               .join('');
        }
        return getA11yTree(document.body, 0).substring(0, 6000);
        """;

    /**
     * Main entry point. Captures a full snapshot of the current browser page.
     *
     * @param driver   active WebDriver pointing at the page under test
     * @param pageName logical name for this page, e.g. "LoginPage"
     */
    public PageSnapshot capture(WebDriver driver, String pageName) {
        waitForPageLoad(driver);

        PageSnapshot snap = new PageSnapshot();
        snap.setPageName(pageName);
        snap.setPageUrl(driver.getCurrentUrl());
        snap.setPageTitle(driver.getTitle());

        // 1. Interactable elements (structured)
        snap.setElements(extractElements(driver));

        // 2. Accessibility tree (text block for prompt)
        snap.setAccessibilityTree(extractA11yTree(driver));

        // 3. Filtered HTML (forms + interactive tags only)
        snap.setFilteredHtml(extractFilteredHtml(driver));

        // 4. Screenshot (base64, for vision mode)
        snap.setScreenshotBase64(captureScreenshot(driver));

        log.info("[UISpecCapture] Captured {} elements from {}", snap.getElements().size(), pageName);
        return snap;
    }

    /**
     * Convenience overload that uses the current thread's driver from DriverFactory.
     */
    public PageSnapshot capture(String pageName) {
        return capture(driverFactory.getDriver(), pageName);
    }

    // ── private helpers ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ElementInfo> extractElements(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        List<Map<String, Object>> raw =
            (List<Map<String, Object>>) js.executeScript(EXTRACT_ELEMENTS_JS);

        return raw.stream().map(m -> {
            ElementInfo e = new ElementInfo();
            e.setTag(str(m, "tag"));
            e.setId(str(m, "id"));
            e.setName(str(m, "name"));
            e.setType(str(m, "type"));
            e.setText(str(m, "text"));
            e.setPlaceholder(str(m, "placeholder"));
            e.setAriaLabel(str(m, "ariaLabel"));
            e.setAriaRole(str(m, "ariaRole"));
            e.setDataTestId(str(m, "dataTestId"));
            e.setDataQa(str(m, "dataQa"));
            e.setClasses((List<String>) m.getOrDefault("classes", List.of()));
            e.setHref(str(m, "href"));
            e.setRequired((Boolean) m.getOrDefault("isRequired", false));
            e.setDisabled((Boolean) m.getOrDefault("isDisabled", false));
            e.setOuterHtml(str(m, "outerHTML"));
            e.setLocatorHint(deriveLocatorHint(e));
            return e;
        }).collect(Collectors.toList());
    }

    private String extractA11yTree(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            return (String) js.executeScript(EXTRACT_A11Y_JS);
        } catch (Exception e) {
            log.warn("[UISpecCapture] Accessibility tree extraction failed: {}", e.getMessage());
            return "(accessibility tree unavailable)";
        }
    }

    private String extractFilteredHtml(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String body = (String) js.executeScript(
                "return document.body.innerHTML.substring(0, 20000);"
            );
            // Remove script/style blocks
            body = body.replaceAll("(?is)<script[^>]*>.*?</script>", "");
            body = body.replaceAll("(?is)<style[^>]*>.*?</style>", "");
            // Remove svg blobs
            body = body.replaceAll("(?is)<svg[^>]*>.*?</svg>", "<svg/>");
            // Collapse whitespace
            body = body.replaceAll("\\s{3,}", "  ");
            return body.substring(0, Math.min(body.length(), 12000));
        } catch (Exception e) {
            log.warn("[UISpecCapture] HTML extraction failed: {}", e.getMessage());
            return "(HTML extraction failed)";
        }
    }

    private String captureScreenshot(WebDriver driver) {
        try {
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[UISpecCapture] Screenshot capture failed: {}", e.getMessage());
            return null;
        }
    }

    private void waitForPageLoad(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(d -> ((JavascriptExecutor) d)
                    .executeScript("return document.readyState")
                    .equals("complete"));
            // Extra wait for SPA frameworks to settle
            Thread.sleep(500);
        } catch (Exception ignored) {}
    }

    /**
     * Derives the best locator hint for an element using the priority:
     * data-testid > data-qa > id > aria-label > name > css classes > tag+text
     */
    private String deriveLocatorHint(ElementInfo e) {
        if (e.getDataTestId() != null)
            return "[data-testid='" + e.getDataTestId() + "']";
        if (e.getDataQa() != null)
            return "[data-qa='" + e.getDataQa() + "']";
        if (e.getId() != null && !e.getId().isEmpty())
            return "#" + e.getId();
        if (e.getAriaLabel() != null)
            return "[aria-label='" + e.getAriaLabel() + "']";
        if (e.getName() != null && !e.getName().isEmpty())
            return "[name='" + e.getName() + "']";
        if (e.getClasses() != null && !e.getClasses().isEmpty())
            return e.getTag() + "." + String.join(".", e.getClasses());
        if (e.getText() != null && !e.getText().isEmpty() && e.getText().length() < 30)
            return "//" + e.getTag() + "[contains(text(),'" + e.getText() + "')]";
        return e.getTag();
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }
}
