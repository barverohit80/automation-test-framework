package com.automation.steps;

import com.automation.driver.DriverFactory;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * Debug step to trace exactly what's happening in locator extraction.
 * Shows each element found and why it's being included or filtered.
 */
@Slf4j
public class DebugLocatorSteps {

    @Autowired private DriverFactory driverFactory;

    /**
     * Run extraction with verbose logging to see what's being captured and filtered.
     */
    @Then("debug extract locators for page {string}")
    public void debugExtractLocatorsForPage(String pageName) {
        log.info("════════════════════════════════════════════════════════════════");
        log.info("DEBUG LOCATOR EXTRACTION");
        log.info("════════════════════════════════════════════════════════════════");

        try {
            Thread.sleep(3000);

            JavascriptExecutor js = (JavascriptExecutor) driverFactory.getDriver();

            // Step 1: Count all elements
            long totalElements = (Long) js.executeScript("""
                return document.querySelectorAll('*').length;
                """);
            log.info("Step 1: Total elements in DOM: {}", totalElements);

            // Step 2: Find all button-like elements
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allInteractive = (List<Map<String, Object>>) js.executeScript("""
                const results = [];
                const tags = ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM', 'DIV', 'SPAN'];

                document.querySelectorAll('*').forEach((el, idx) => {
                    if (!tags.includes(el.tagName)) return;

                    const style = window.getComputedStyle(el);
                    const info = {
                        index: idx,
                        tag: el.tagName,
                        id: el.id || 'NONE',
                        name: el.name || 'NONE',
                        text: (el.textContent || '').trim().substring(0, 30),
                        display: style.display,
                        visibility: style.visibility,
                        opacity: style.opacity,
                        ariaHidden: el.getAttribute('aria-hidden'),
                        role: el.getAttribute('role'),
                        onclick: !!el.getAttribute('onclick'),
                        offsetParent: !!el.offsetParent
                    };
                    results.push(info);
                });

                return results;
                """);

            log.info("Step 2: Found {} matching tag elements", allInteractive.size());

            if (allInteractive.isEmpty()) {
                log.warn("⚠ NO elements with interactable tags found!");
                log.info("Try expanding tag list or checking page structure");
                return;
            }

            // Step 3: Show each element and why it would be filtered
            int included = 0;
            for (Map<String, Object> el : allInteractive) {
                String tag = (String) el.get("tag");
                String id = (String) el.get("id");
                String display = (String) el.get("display");
                String visibility = (String) el.get("visibility");
                String opacity = (String) el.get("opacity");
                String ariaHidden = (String) el.get("ariaHidden");
                String text = (String) el.get("text");
                String name = (String) el.get("name");
                Boolean offsetParent = (Boolean) el.get("offsetParent");
                Boolean onclick = (Boolean) el.get("onclick");
                String role = (String) el.get("role");

                // Check if element would be included
                boolean skip = false;
                String reason = "";

                if ("none".equalsIgnoreCase(display)) {
                    skip = true;
                    reason = "display:none";
                }
                if ("hidden".equalsIgnoreCase(visibility)) {
                    skip = true;
                    reason = reason.isEmpty() ? "visibility:hidden" : reason + ", visibility:hidden";
                }
                if ("0".equals(opacity)) {
                    skip = true;
                    reason = reason.isEmpty() ? "opacity:0" : reason + ", opacity:0";
                }
                if ("true".equals(ariaHidden)) {
                    skip = true;
                    reason = reason.isEmpty() ? "aria-hidden" : reason + ", aria-hidden";
                }

                // For DIV/SPAN, check if interactive
                if (("DIV".equals(tag) || "SPAN".equals(tag)) && !("button".equalsIgnoreCase(role) || onclick)) {
                    skip = true;
                    reason = reason.isEmpty() ? "DIV/SPAN not interactive" : reason + ", not interactive";
                }

                // Check if has identifying info
                if (!skip && "NONE".equals(id) && "NONE".equals(name) && text.isEmpty() && "NONE".equals(role)) {
                    skip = true;
                    reason = reason.isEmpty() ? "no identifying info" : reason + ", no id/name/text";
                }

                String status = skip ? "❌ SKIP" : "✓ INCLUDE";
                String reasonStr = skip ? " (" + reason + ")" : "";

                log.info("{} {}: id={} name={} text='{}'{}",
                    status, tag, id, name, text, reasonStr);

                if (!skip) {
                    included++;
                }
            }

            log.info("════════════════════════════════════════════════════════════════");
            log.info("RESULT: {} out of {} elements would be included", included, allInteractive.size());
            log.info("════════════════════════════════════════════════════════════════");

            if (included == 0) {
                log.warn("⚠ ALL elements would be filtered out!");
                log.warn("Reasons to check:");
                log.warn("  - Elements have display:none");
                log.warn("  - Elements have visibility:hidden");
                log.warn("  - Elements have opacity:0");
                log.warn("  - DIVs/SPANs without role='button' or onclick");
                log.warn("  - Elements have no id, name, or text");
                log.warn("");
                log.warn("SOLUTIONS:");
                log.warn("  1. Remove style filters (allow display:none)");
                log.warn("  2. Include more tag types");
                log.warn("  3. Include elements even with no identifying info");
            }

        } catch (Exception e) {
            log.error("Debug extraction failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Run a simple extraction that captures EVERYTHING without filtering.
     */
    @Then("ultra-simple extract all elements")
    public void ultraSimpleExtract() {
        log.info("════════════════════════════════════════════════════════════════");
        log.info("ULTRA-SIMPLE EXTRACTION (no filtering)");
        log.info("════════════════════════════════════════════════════════════════");

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> allElements = (List<Map<String, Object>>) ((JavascriptExecutor) driverFactory.getDriver())
                .executeScript("""
                    const results = [];
                    let count = 0;

                    // Get ALL interactive elements without strict filtering
                    document.querySelectorAll('button, input, select, textarea, a, label, form, [role="button"]')
                        .forEach((el) => {
                            // Only skip if truly invisible
                            const style = window.getComputedStyle(el);
                            if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
                                return;
                            }

                            count++;
                            const name = el.id || el.name || el.getAttribute('data-testid')
                                || el.getAttribute('aria-label') || el.textContent.substring(0, 20)
                                || el.tagName.toLowerCase() + '_' + count;

                            results.push({
                                elementName: name.replace(/[^a-zA-Z0-9_]/g, '_'),
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

                    return results;
                    """);

            log.info("Total elements extracted: {}", allElements.size());

            if (allElements.isEmpty()) {
                log.warn("Still empty! Page might have:");
                log.warn("  - No interactive elements");
                log.warn("  - Elements inside iframes");
                log.warn("  - Custom web components");
                log.warn("  - Dynamically loaded after initial render");
            } else {
                log.info("Elements found:");
                allElements.forEach(el ->
                    log.info("  - {}: {} ({})", el.get("elementName"), el.get("tag"), el.get("id"))
                );
            }

        } catch (Exception e) {
            log.error("Ultra-simple extraction failed: {}", e.getMessage(), e);
        }
    }
}
