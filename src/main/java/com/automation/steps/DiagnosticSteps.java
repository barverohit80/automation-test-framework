package com.automation.steps;

import com.automation.driver.DriverFactory;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * Diagnostic steps to debug why locator extraction returns empty lists.
 *
 * Usage in feature files:
 *   Then print DOM diagnostic report
 *   Then print all elements matching interactable tags
 *   Then print all form inputs on page
 */
@Slf4j
public class DiagnosticSteps {

    @Autowired private DriverFactory driverFactory;

    /**
     * Print comprehensive DOM diagnostic report.
     * Helps identify why extraction might be failing.
     */
    @Then("print DOM diagnostic report")
    public void printDomDiagnosticReport() {
        JavascriptExecutor js = (JavascriptExecutor) driverFactory.getDriver();

        Map<String, Object> report = (Map<String, Object>) js.executeScript("""
            return {
                pageTitle: document.title,
                pageUrl: window.location.href,
                documentReady: document.readyState,
                bodyChildren: document.body.children.length,
                totalElements: document.querySelectorAll('*').length,

                // Count by tag
                buttons: document.querySelectorAll('button').length,
                inputs: document.querySelectorAll('input').length,
                selects: document.querySelectorAll('select').length,
                textareas: document.querySelectorAll('textarea').length,
                anchors: document.querySelectorAll('a').length,
                labels: document.querySelectorAll('label').length,
                forms: document.querySelectorAll('form').length,

                // Hidden elements
                hiddenByDisplayNone: document.querySelectorAll('[style*="display: none"]').length,
                hiddenByAriaHidden: document.querySelectorAll('[aria-hidden="true"]').length,
                hiddenByOpacity0: document.querySelectorAll('[style*="opacity: 0"]').length,

                // Custom elements (web components)
                customElements: Array.from(document.querySelectorAll('*'))
                    .filter(el => el.tagName.includes('-')).length,

                // DIV/SPAN elements (might contain interactive content)
                divs: document.querySelectorAll('div').length,
                spans: document.querySelectorAll('span').length
            };
            """);

        log.info("════════════════════════════════════════════════════════════════");
        log.info("DOM DIAGNOSTIC REPORT");
        log.info("════════════════════════════════════════════════════════════════");
        log.info("Page Title: {}", report.get("pageTitle"));
        log.info("Page URL: {}", report.get("pageUrl"));
        log.info("Document Ready: {}", report.get("documentReady"));
        log.info("Total Elements: {}", report.get("totalElements"));
        log.info("Body Children: {}", report.get("bodyChildren"));
        log.info("────────────────────────────────────────────────────────────────");
        log.info("INTERACTABLE ELEMENTS (should extract these):");
        log.info("  Buttons: {}", report.get("buttons"));
        log.info("  Inputs: {}", report.get("inputs"));
        log.info("  Selects: {}", report.get("selects"));
        log.info("  Textareas: {}", report.get("textareas"));
        log.info("  Anchors: {}", report.get("anchors"));
        log.info("  Labels: {}", report.get("labels"));
        log.info("  Forms: {}", report.get("forms"));
        log.info("────────────────────────────────────────────────────────────────");
        log.info("POTENTIALLY HIDDEN ELEMENTS:");
        log.info("  display:none: {}", report.get("hiddenByDisplayNone"));
        log.info("  aria-hidden: {}", report.get("hiddenByAriaHidden"));
        log.info("  opacity:0: {}", report.get("hiddenByOpacity0"));
        log.info("────────────────────────────────────────────────────────────────");
        log.info("OTHER ELEMENTS (might need custom handling):");
        log.info("  Custom elements (web-components): {}", report.get("customElements"));
        log.info("  DIVs: {}", report.get("divs"));
        log.info("  SPANs: {}", report.get("spans"));
        log.info("════════════════════════════════════════════════════════════════");
    }

    /**
     * List all elements matching the interactable tag filter.
     */
    @Then("print all elements matching interactable tags")
    public void printInteractableElements() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) ((JavascriptExecutor) driverFactory.getDriver())
                .executeScript("""
                    const INTERACTABLE_TAGS = ['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM'];
                    const elements = [];

                    document.querySelectorAll('*').forEach((el) => {
                        if (!INTERACTABLE_TAGS.includes(el.tagName)) return;

                        const style = window.getComputedStyle(el);
                        const isVisible = style.display !== 'none'
                            && style.visibility !== 'hidden'
                            && style.opacity !== '0'
                            && el.getAttribute('aria-hidden') !== 'true';

                        elements.push({
                            tag: el.tagName,
                            id: el.id || '(no id)',
                            name: el.name || '(no name)',
                            className: el.className || '(no class)',
                            text: (el.textContent || '').substring(0, 30),
                            visible: isVisible,
                            opacity: style.opacity,
                            display: style.display,
                            visibility: style.visibility
                        });
                    });

                    return elements;
                    """);

        log.info("════════════════════════════════════════════════════════════════");
        log.info("ALL INTERACTABLE ELEMENTS (BUTTON, INPUT, SELECT, etc.)");
        log.info("════════════════════════════════════════════════════════════════");

        if (elements == null || elements.isEmpty()) {
            log.warn("⚠ NO INTERACTABLE ELEMENTS FOUND!");
        } else {
            for (int i = 0; i < elements.size(); i++) {
                Map<String, Object> el = elements.get(i);
                log.info("{}: {} id={} name={} visible={}",
                    i+1, el.get("tag"), el.get("id"), el.get("name"), el.get("visible"));
                if (!el.get("text").equals("")) {
                    log.info("   text: {}", el.get("text"));
                }
            }
        }

        log.info("════════════════════════════════════════════════════════════════");
        log.info("TOTAL: {} elements", elements != null ? elements.size() : 0);
        log.info("════════════════════════════════════════════════════════════════");
    }

    /**
     * List all input elements (including hidden ones).
     */
    @Then("print all form inputs on page")
    public void printAllInputs() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) ((JavascriptExecutor) driverFactory.getDriver())
                .executeScript("""
                    return Array.from(document.querySelectorAll('input, select, textarea, button'))
                        .map(el => ({
                            tag: el.tagName,
                            type: el.type || 'N/A',
                            id: el.id || '(no id)',
                            name: el.name || '(no name)',
                            placeholder: el.placeholder || '(no placeholder)',
                            visible: el.offsetParent !== null,
                            display: window.getComputedStyle(el).display,
                            value: (el.value || '').substring(0, 20)
                        }));
                    """);

        log.info("════════════════════════════════════════════════════════════════");
        log.info("ALL FORM INPUTS (including hidden)");
        log.info("════════════════════════════════════════════════════════════════");

        if (inputs == null || inputs.isEmpty()) {
            log.warn("⚠ NO FORM INPUTS FOUND!");
        } else {
            inputs.forEach(input -> {
                log.info("{}: {} (id={}, name={}, visible={})",
                    input.get("tag"), input.get("type"), input.get("id"), input.get("name"), input.get("visible"));
            });
        }

        log.info("════════════════════════════════════════════════════════════════");
        log.info("TOTAL: {} inputs", inputs != null ? inputs.size() : 0);
        log.info("════════════════════════════════════════════════════════════════");
    }

    /**
     * Check if page is fully loaded.
     */
    @Then("print page load status")
    public void printPageLoadStatus() {
        Map<String, Object> status = (Map<String, Object>) ((JavascriptExecutor) driverFactory.getDriver())
                .executeScript("""
                    return {
                        documentReady: document.readyState,
                        bodyLoaded: !!document.body,
                        pageVisible: !document.hidden,
                        jQueryReady: typeof jQuery !== 'undefined',
                        angularReady: typeof angular !== 'undefined',
                        reactReady: typeof React !== 'undefined',
                        timeSinceLoad: performance.now()
                    };
                    """);

        log.info("════════════════════════════════════════════════════════════════");
        log.info("PAGE LOAD STATUS");
        log.info("════════════════════════════════════════════════════════════════");
        log.info("Document Ready State: {}", status.get("documentReady"));
        log.info("Body Loaded: {}", status.get("bodyLoaded"));
        log.info("Page Visible: {}", status.get("pageVisible"));
        log.info("Time Since Load: {} ms", status.get("timeSinceLoad"));
        log.info("────────────────────────────────────────────────────────────────");
        log.info("FRAMEWORKS DETECTED:");
        log.info("  jQuery: {}", status.get("jQueryReady"));
        log.info("  Angular: {}", status.get("angularReady"));
        log.info("  React: {}", status.get("reactReady"));
        log.info("════════════════════════════════════════════════════════════════");
    }
}
