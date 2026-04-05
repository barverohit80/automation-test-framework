package com.automation.locator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single locator strategy: type (id, css, xpath, name) + value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocatorEntry {

    /** Locator type: "id", "css", "xpath", "name", "linkText", "className" */
    private String type;

    /** Locator value, e.g. "#userName" or "//input[@id='userName']" */
    private String value;

    /** Confidence score 0.0–1.0 based on locator stability */
    private double confidence;

    /** Human-readable description of why this locator was chosen */
    private String description;
}
