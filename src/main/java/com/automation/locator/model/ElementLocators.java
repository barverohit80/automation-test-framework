package com.automation.locator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * All locator strategies for a single UI element.
 * Contains a primary locator and ranked fallbacks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ElementLocators {

    /** Logical name for this element, e.g. "usernameInput", "loginButton" */
    private String elementName;

    /** The preferred locator — tried first */
    private LocatorEntry primary;

    /** Fallback locators — tried in order if primary fails */
    private List<LocatorEntry> fallbacks;

    /** Element tag for context, e.g. "input", "button" */
    private String tag;

    /** Timestamp of when these locators were last extracted/validated */
    private String lastCaptured;
}
