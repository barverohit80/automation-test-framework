package com.automation.steps;

import com.automation.driver.DriverFactory;
import com.automation.locator.extractor.LocatorExtractor;
import com.automation.locator.store.LocatorStore;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Step definitions for generating and saving locators.
 *
 * Usage in feature files:
 *   Then generate locators for page "LoginPage"
 *   Then generate locators for page "PracticeFormPage"
 *
 * This extracts all interactable DOM elements from the current page
 * and saves them as ranked locators to:
 *   src/main/resources/locators/{PageName}.json
 *
 * Must be called AFTER navigating to the target page.
 */
@Slf4j
public class LocatorSteps {

    @Autowired private DriverFactory driverFactory;
    @Autowired private LocatorExtractor locatorExtractor;
    @Autowired private LocatorStore locatorStore;

    /**
     * Generate and save locators for the current page.
     *
     * @param pageName Logical page name (e.g., "LoginPage", "PracticeFormPage")
     *
     * Example usage:
     *   Given the user is on the practice form page
     *   Then generate locators for page "PracticeFormPage"
     */
    @Then("generate locators for page {string}")
    public void generateLocatorsForPage(String pageName) {
        log.info("[LocatorGeneration] Generating locators for page: {}", pageName);

        try {
            // Get current page URL and driver
            String pageUrl = driverFactory.getDriver().getCurrentUrl();

            // Extract all interactable elements from DOM
            var pageLocators = locatorExtractor.extract(
                    driverFactory.getDriver(),
                    pageName,
                    pageUrl
            );

            // Save to JSON file
            locatorStore.save(pageLocators);

            log.info("[LocatorGeneration] ✓ Saved {} elements to {}.json",
                    pageLocators.getElements().size(), pageName);

        } catch (Exception e) {
            log.error("[LocatorGeneration] ✗ Failed to generate locators for page '{}': {}",
                    pageName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate locators for page: " + pageName, e);
        }
    }

    /**
     * Generate and save locators with automatic page name derivation.
     *
     * Derives page name from the current URL (attempts to infer from domain/path).
     * Falls back to a generic name if derivation fails.
     *
     * Example usage:
     *   Given the user navigates to "https://demoqa.com/automation-practice-form"
     *   Then generate locators for current page
     */
    @Then("generate locators for current page")
    public void generateLocatorsForCurrentPage() {
        log.info("[LocatorGeneration] Generating locators for current page");

        try {
            // Get current page info
            String pageUrl = driverFactory.getDriver().getCurrentUrl();
            String pageName = derivePageNameFromUrl(pageUrl);

            log.info("[LocatorGeneration] Derived page name from URL: {}", pageName);

            // Extract all interactable elements from DOM
            var pageLocators = locatorExtractor.extract(
                    driverFactory.getDriver(),
                    pageName,
                    pageUrl
            );

            // Save to JSON file
            locatorStore.save(pageLocators);

            log.info("[LocatorGeneration] ✓ Saved {} elements to {}.json",
                    pageLocators.getElements().size(), pageName);

        } catch (Exception e) {
            log.error("[LocatorGeneration] ✗ Failed to generate locators: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate locators for current page", e);
        }
    }

    /**
     * Derive a page name from the current URL.
     *
     * Examples:
     *   https://demoqa.com/automation-practice-form → PracticeFormPage
     *   https://demoqa.com/login → LoginPage
     *   https://demoqa.com/web-tables → WebTablesPage
     */
    private String derivePageNameFromUrl(String url) {
        try {
            // Extract path from URL
            java.net.URL urlObj = new java.net.URL(url);
            String path = urlObj.getPath();

            // Get last segment of path (e.g., /automation-practice-form → practice-form)
            String[] segments = path.split("/");
            String pagePart = segments[segments.length - 1];

            // Handle empty or index pages
            if (pagePart.isEmpty() || pagePart.equals("index.html")) {
                pagePart = urlObj.getHost().split("\\.")[0]; // Use domain name
            }

            // Convert kebab-case to PascalCase
            return convertKebabCaseToPascalCase(pagePart) + "Page";

        } catch (Exception e) {
            log.warn("[LocatorGeneration] Could not derive page name from URL: {}, using GenericPage", url);
            return "GenericPage";
        }
    }

    /**
     * Convert kebab-case string to PascalCase.
     * Examples:
     *   automation-practice-form → AutomationPracticeForm
     *   login → Login
     *   web-tables → WebTables
     */
    private String convertKebabCaseToPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return "Generic";
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (c == '-' || c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }
}
