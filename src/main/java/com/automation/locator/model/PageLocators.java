package com.automation.locator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Root JSON model — all locators for a single page.
 * Serialized to: resources/locators/{pageName}.json
 *
 * Example JSON:
 * {
 *   "pageName": "LoginPage",
 *   "pageUrl": "https://demoqa.com/login",
 *   "elements": [
 *     {
 *       "elementName": "usernameInput",
 *       "tag": "input",
 *       "primary": { "type": "id", "value": "userName", "confidence": 1.0, "description": "stable id attribute" },
 *       "fallbacks": [
 *         { "type": "css", "value": "input[placeholder='UserName']", "confidence": 0.8, "description": "placeholder attribute" },
 *         { "type": "xpath", "value": "//input[@type='text' and @placeholder='UserName']", "confidence": 0.6, "description": "type+placeholder combo" }
 *       ],
 *       "lastCaptured": "2026-04-05T14:30:00"
 *     }
 *   ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageLocators {

    private String pageName;
    private String pageUrl;
    private List<ElementLocators> elements;
}
