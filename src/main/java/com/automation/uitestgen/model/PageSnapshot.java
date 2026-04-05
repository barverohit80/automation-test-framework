package com.automation.uitestgen.model;

import lombok.Data;
import java.util.List;

/**
 * Complete snapshot of a page — this is the input to the LLM.
 */
@Data
public class PageSnapshot {

    /** Logical name, e.g. "LoginPage", "CheckoutPage" */
    private String pageName;

    private String pageUrl;
    private String pageTitle;

    /** Structured list of interactable elements extracted from DOM */
    private List<ElementInfo> elements;

    /** ARIA accessibility tree as readable text */
    private String accessibilityTree;

    /** Filtered HTML (no scripts/styles) — used in the LLM prompt */
    private String filteredHtml;

    /** Base64 PNG screenshot — for vision-mode prompt (optional) */
    private String screenshotBase64;

    /**
     * Human-provided description of what user actions to automate.
     * E.g. "login with valid credentials and verify dashboard appears"
     */
    private String testScenarioDescription;

    /** Tags to apply to generated Cucumber scenarios */
    private List<String> tags;

    /** Existing step expressions to avoid duplicating */
    private List<String> existingSteps;
}
