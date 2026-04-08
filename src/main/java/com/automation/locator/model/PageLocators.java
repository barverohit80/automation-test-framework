package com.automation.locator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Root model for locator persistence.
 * Represents all locators for a single page.
 * Serialized to/from JSON: resources/locators/{pageName}.json
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageLocators {

    /** Logical page name, e.g. "LoginPage" (matches filename without .json) */
    private String pageName;

    /** Page URL for context */
    private String pageUrl;

    /** List of all elements on this page with their locator strategies */
    private List<ElementLocators> elements;

    /** Timestamp of when these locators were last captured/updated */
    private String lastUpdated;
}
