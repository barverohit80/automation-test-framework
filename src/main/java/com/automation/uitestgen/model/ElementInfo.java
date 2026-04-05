package com.automation.uitestgen.model;

import lombok.Data;
import java.util.List;

/**
 * One interactable element extracted from the DOM.
 * Populated by UISpecCapture, consumed by UIPromptBuilder.
 */
@Data
public class ElementInfo {
    private String tag;           // input, button, a, select, etc.
    private String id;
    private String name;
    private String type;          // text, password, email, submit, etc.
    private String text;          // visible text / value
    private String placeholder;
    private String ariaLabel;
    private String ariaRole;
    private String dataTestId;
    private String dataQa;
    private List<String> classes; // only stable class names (no hash classes)
    private String href;
    private boolean required;
    private boolean disabled;
    private String outerHtml;     // first 300 chars of raw HTML

    /** Best locator hint derived by UISpecCapture.deriveLocatorHint() */
    private String locatorHint;
}
