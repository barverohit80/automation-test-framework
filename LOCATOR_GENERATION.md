# Self-Healing Locators: Generation Guide

This document explains how to generate and use self-healing locators in your test scenarios.

## Quick Start

### Option 1: Generate with Explicit Page Name (Recommended)

```gherkin
@generate
Scenario: Submit practice form
  Given the user is on the practice form page
  Then generate locators for page "PracticeFormPage"
  When the user fills in the form with data
  Then verify form submission
```

**Benefits:**
- Clear and explicit page naming
- No guessing or derivation
- Easy to understand which page is being tested
- Locators saved to `src/main/resources/locators/PracticeFormPage.json`

### Option 2: Auto-Derive Page Name from URL (Fallback)

```gherkin
@generate
Scenario: Submit practice form
  Given the user navigates to "https://demoqa.com/automation-practice-form"
  Then generate locators for current page
  When the user fills in the form with data
  Then verify form submission
```

**How it works:**
- Extracts path from URL: `/automation-practice-form`
- Converts to PascalCase: `AutomationPracticeForm`
- Adds "Page" suffix: `AutomationPracticeFormPage`
- Saves to `src/main/resources/locators/AutomationPracticeFormPage.json`

## How It Works

### 1. **Navigation** (Your Step)
```gherkin
Given the user is on the practice form page  ← Page loads here
```
After this step, the browser is on the target page.

### 2. **Locator Generation** (Explicit Step)
```gherkin
Then generate locators for page "PracticeFormPage"
```
This step:
- ✅ Extracts all interactable DOM elements (buttons, inputs, links, etc.)
- ✅ Generates ranked locator strategies (primary + fallbacks)
- ✅ Saves to JSON: `src/main/resources/locators/PracticeFormPage.json`

### 3. **Test Steps** (Your Steps)
```gherkin
When the user fills in the form with data
```
Your test steps can now use resilient locators via BasePage methods.

## Generated Locators File

Each `{PageName}.json` contains:

```json
{
  "pageName": "PracticeFormPage",
  "pageUrl": "https://demoqa.com/automation-practice-form",
  "elements": [
    {
      "elementName": "firstNameInput",
      "tag": "input",
      "primary": {
        "type": "id",
        "value": "firstName",
        "confidence": 0.95,
        "description": "Unique ID attribute"
      },
      "fallbacks": [
        {
          "type": "name",
          "value": "firstName",
          "confidence": 0.90,
          "description": "Name attribute on form element"
        },
        {
          "type": "xpath",
          "value": "//input[@placeholder='First Name']",
          "confidence": 0.70,
          "description": "Placeholder attribute match"
        }
      ],
      "lastCaptured": "2026-04-09T10:30:00"
    }
  ],
  "lastUpdated": "2026-04-09T10:30:00"
}
```

## Using Generated Locators

Once locators are generated, use them in your Page Objects:

```java
@Component
@Scope("cucumber-glue")
public class PracticeFormPage extends BasePage {

    private static final String PAGE_NAME = "PracticeFormPage";

    public void fillFirstName(String firstName) {
        resilientType(PAGE_NAME, "firstNameInput", firstName);
    }

    public void clickSubmit() {
        resilientClick(PAGE_NAME, "submitButton");
    }

    public String getSuccessMessage() {
        return resilientGetText(PAGE_NAME, "successMessage");
    }
}
```

## Confidence Scoring System

Locators are ranked by confidence (stability):

| Score | Locator Type | Example | Stability |
|-------|----------|---------|-----------|
| 1.0 | data-testid | `[data-testid='submit']` | Most stable (explicit) |
| 0.95 | id | `#firstName` | Very stable (usually unique) |
| 0.90 | name | `[name='firstName']` | Stable (form elements) |
| 0.85 | aria-label | `[aria-label='Submit']` | Good (semantic) |
| 0.80 | CSS class | `.btn-primary` | Medium (may be fragile) |
| 0.70 | Placeholder | `[placeholder='Enter name']` | Medium |
| 0.65 | Exact text | `//button[text()='Submit']` | Lower (fragile) |
| 0.50 | Element index | `//button[3]` | Low (fragile) |
| 0.30 | Positional XPath | `(//button)[3]` | Very low (fragile) |
| 0.2 | Full absolute XPath | `//div[1]/form[1]/button[2]` | Fragile (fallback only) |

## Self-Healing in Action

When a test runs with resilient locators:

```
1. Try PRIMARY locator (highest confidence)
   → If found ✓, use it
   → If not found, log warning

2. Try FALLBACK locators (in order of confidence)
   → If found ✓, use it & log "Fallback used"
   → If not found, try next fallback

3. If ALL locators fail
   → ✗ Throw NoSuchElementException
```

Example logs:
```
[ResilientLocator] Element 'submitButton' found with PRIMARY locator (id)
[ResilientLocator] Element 'formTitle' found with FALLBACK locator (xpath), confidence: 0.70
[ResilientLocator] Element 'errorMessage' not found with any locator
```

## Example Feature File

```gherkin
@forms @generate
Feature: Practice Form Submission

  @smoke
  Scenario: Submit form with valid data
    # Step 1: Navigate to page
    Given the user is on the practice form page

    # Step 2: Generate locators (REQUIRED for new pages)
    Then generate locators for page "PracticeFormPage"

    # Step 3: Test steps use generated locators
    When the user fills in first name "John"
    And the user fills in last name "Doe"
    And the user clicks submit

    # Step 4: Verify
    Then success message should be displayed
```

## Step Definition Reference

### Generate with Explicit Page Name
```gherkin
Then generate locators for page "PageName"
```
- **Required:** Scenario must navigate to target page first
- **Page name:** User-provided, matches class name (e.g., "PracticeFormPage")
- **Output:** `src/main/resources/locators/PageName.json`

### Generate with URL Auto-Derivation
```gherkin
Then generate locators for current page
```
- **Auto-derives:** Page name from current URL path
- **Examples:**
  - `https://demoqa.com/automation-practice-form` → `AutomationPracticeFormPage`
  - `https://demoqa.com/login` → `LoginPage`
  - `https://demoqa.com/web-tables` → `WebTablesPage`
- **Output:** `src/main/resources/locators/{DerivedName}.json`

## Common Patterns

### Pattern 1: New Page with Explicit Name
```gherkin
Given the user is on the [page name] page
Then generate locators for page "[PageName]"
When <test steps>
Then <assertions>
```

### Pattern 2: Multiple Pages in One Scenario
```gherkin
Given the user is on the login page
Then generate locators for page "LoginPage"
When the user logs in with "user@example.com"

And the user navigates to dashboard
Then generate locators for page "DashboardPage"
When the user clicks profile
```

### Pattern 3: Reuse Existing Locators
```gherkin
# If PracticeFormPage.json already exists, skip generation:
Given the user is on the practice form page
When the user fills in the form
Then submission succeeds
# Locators will auto-extract on first access if missing
```

## Troubleshooting

### Issue: JSON file not created

**Check 1:** Did you call the generate step?
```gherkin
Given the user is on the practice form page
Then generate locators for page "PracticeFormPage"  ← Required!
```

**Check 2:** Is the page fully loaded?
- Make sure page navigation is complete before calling generate
- Check console logs for extraction errors

**Check 3:** Are there interactable elements on the page?
- Extraction only captures: button, input, select, textarea, a, label, form
- Hidden elements are skipped

### Issue: Resilient locators failing

**Step 1:** Check if locators JSON exists
```bash
ls src/main/resources/locators/PageName.json
```

**Step 2:** Verify element names match
```java
// Feature: PracticeFormPage.json has "firstNameInput"
resilientType("PracticeFormPage", "firstNameInput", "John");
//                                                     ^^^^^^^^ must match JSON
```

**Step 3:** Check confidence scores
- If all locators are low confidence, page might have changed
- Regenerate locators to get fresh strategies

## Best Practices

✅ **DO:**
- Generate locators immediately after page load
- Use explicit page names (not auto-derived)
- Name elements semantically (e.g., "submitButton", not "button_2")
- Tag scenarios with `@generate` when generating new locators
- Regenerate locators if page structure changes significantly

❌ **DON'T:**
- Generate locators before page navigation completes
- Use generic names like "element1", "element2"
- Rely solely on auto-derived page names
- Commit JSON with hardcoded element names that change
- Generate for static/read-only pages (waste of locators)

## Integration with CI/CD

Generate locators locally during development:
```bash
# Run @generate scenarios to create JSONs
mvn test -Dcucumber.filter.tags="@generate"
```

Then commit the generated JSONs:
```bash
git add src/main/resources/locators/
git commit -m "Add generated locators for PracticeFormPage"
```

In CI, tests use pre-generated locators with auto-fallback if missing.
