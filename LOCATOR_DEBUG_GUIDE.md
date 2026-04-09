# Locator Extraction Debugging Guide

## Problem: Empty Locators List

If you're seeing `elements: []` in your generated JSON files, this guide will help you diagnose and fix the issue.

```json
{
  "pageName": "PracticeFormPage",
  "pageUrl": "https://demoqa.com/automation-practice-form",
  "elements": [],  // ❌ EMPTY!
  "lastUpdated": "2026-04-09T09:44:47"
}
```

## Diagnostic Checklist

### 1. Check Logs During Extraction

When you run a scenario with `@generate` tag, look for these log messages:

```
[LocatorGeneration] Current URL: https://demoqa.com/automation-practice-form
[LocatorGeneration] Page title: Practice Form
[LocatorExtractor] JavaScript returned 23 raw elements from DOM
[LocatorGeneration] ✓ Saved 23 elements to PracticeFormPage.json
```

**If you see:**
```
[LocatorExtractor] JavaScript returned 0 raw elements from DOM
[LocatorGeneration] ⚠ No elements extracted for page 'PracticeFormPage'
```

Then no interactable elements were found. Continue to step 2.

### 2. Verify Page is Fully Loaded

The extraction waits 2 seconds by default. If your page takes longer to load:

**Issue:** Page still loading when extraction runs
**Solution:** Add explicit wait before generating locators

In your feature file:
```gherkin
Given the user is on the practice form page
Then the user waits for element with id "firstName" to be visible
Then generate locators for page "PracticeFormPage"
```

Or modify LocatorSteps to increase wait time:
```java
// In generateLocatorsForPage() method
Thread.sleep(5000);  // Wait 5 seconds instead of 2
```

### 3. Check What Elements Exist on the Page

Add a debug step to list all elements on the page:

```gherkin
Then print all elements on the page
```

Add this step definition to LocatorSteps.java:
```java
@Then("print all elements on the page")
public void printAllElements() {
    var result = (List<Map<String, Object>>) ((JavascriptExecutor) driverFactory.getDriver())
            .executeScript("""
                return Array.from(document.querySelectorAll('*')).map(el => ({
                    tag: el.tagName,
                    id: el.id,
                    name: el.name,
                    class: el.className,
                    visible: window.getComputedStyle(el).display !== 'none'
                }));
            """);

    result.forEach(el -> log.info("Element: {}", el));
}
```

### 4. Check Interactable Tag Types

The extraction only looks for these element types:
```
['BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM']
```

**If your page has:**
- Custom buttons (div with role="button") - NOT EXTRACTED
- Complex forms (divs arranged as form) - NOT EXTRACTED
- Web components - NOT EXTRACTED
- SVG elements - NOT EXTRACTED

**Solution:** Expand INTERACTABLE_TAGS in LocatorExtractor.java:

```java
const INTERACTABLE_TAGS = [
    'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM',
    'DIV',  // For custom buttons
    'SPAN'  // For clickable spans
];
```

### 5. Check Visibility Filters

Elements are filtered by visibility:

```javascript
// These are SKIPPED:
if (style.opacity === '0') return;           // Transparent
if (style.visibility === 'hidden') return;   // Hidden via CSS
if (el.getAttribute('aria-hidden') === 'true') return;  // ARIA hidden
```

**If elements are legitimately hidden:**

```javascript
// For modals that become visible: temporarily unhide them
// For collapsed sections: temporarily expand them
// For lazy-loaded content: trigger loading
```

## Common Issues & Solutions

### Issue 1: No Elements on Home Page

**Symptom:**
```
HomePage.json has empty elements []
But home page clearly has buttons and links
```

**Diagnosis:**
Home page might use a different element structure. Check logs:

```bash
# Add debug step
Then print all elements on the page

# Look for tags: button, input, link, etc.
```

**Solution:**
If elements exist but aren't being captured:
1. Check if they're in iframes (not captured)
2. Check if they're lazy-loaded
3. Increase wait time
4. Expand INTERACTABLE_TAGS

### Issue 2: Elements Hidden by CSS

**Symptom:**
```
Page clearly shows form fields
But extraction shows 0 elements
```

**Diagnosis:**
Check element visibility:
```javascript
// Run in browser console
document.querySelectorAll('input').forEach(el => {
    const style = window.getComputedStyle(el);
    console.log(el.id, {
        display: style.display,
        visibility: style.visibility,
        opacity: style.opacity,
        offsetParent: !!el.offsetParent
    });
});
```

**Solution:**
If elements have `display: none` initially but become visible:
- Wait longer for page to fully render
- Or temporarily modify extraction to include hidden elements

### Issue 3: Framework-Specific Elements

**Angular/React/Vue Components:**
Custom elements might not be extracted.

**Symptom:**
```
Page has form-field web components
But extraction shows 0 elements
```

**Solution:**
Expand INTERACTABLE_TAGS to include common component tags:
```javascript
const INTERACTABLE_TAGS = [
    'BUTTON', 'INPUT', 'SELECT', 'TEXTAREA', 'A', 'LABEL', 'FORM',
    'MAT-BUTTON',      // Angular Material
    'MD-BUTTON',       // Material Design
    'FORM-FIELD',      // Custom form fields
    'APP-INPUT'        // App-specific inputs
];
```

## Step-by-Step Debugging Process

### Step 1: Enable Debug Logging
In your test runner (log4j.properties or logback.xml):
```properties
log4j.logger.com.automation=DEBUG
```

### Step 2: Run Extraction with Logs
```bash
mvn test -Dcucumber.filter.tags="@generate" -DlogLevel=DEBUG
```

### Step 3: Analyze Logs
Look for:
```
[LocatorExtractor] JavaScript returned X raw elements
[LocatorExtractor] Extracted Y elements for page
```

### Step 4: Check Generated JSON
```bash
cat src/main/resources/locators/PracticeFormPage.json | jq '.elements | length'
```

### Step 5: If Still Empty, Add Print Step
```gherkin
Given the user is on the practice form page
Then print all elements on the page
Then generate locators for page "PracticeFormPage"
```

### Step 6: Browser Console Debugging
Open browser DevTools during test:
```javascript
// Run in console
document.querySelectorAll('button, input, select, textarea, a, label, form').length
```

## Advanced Debugging

### Option A: Temporarily Disable Visibility Filters

Edit LocatorExtractor.java:
```java
// Comment out strict visibility checks
// if (!el.offsetParent) return; // Hidden element
```

Run extraction again to see if elements are captured.

### Option B: Log Raw JavaScript Result

Edit LocatorExtractor.extract():
```java
if (elementData != null) {
    log.info("[DEBUG] Raw elements from JS: {}", elementData);
}
```

### Option C: Extract Elements Manually

In a test step:
```java
@Then("debug extract all elements")
public void debugExtract() {
    var result = locatorExtractor.extract(
        driverFactory.getDriver(),
        "DebugPage",
        driverFactory.getDriver().getCurrentUrl()
    );

    log.info("Total elements: {}", result.getElements().size());
    result.getElements().forEach(el ->
        log.info("  - {}: {} ({})", el.getElementName(), el.getTag(), el.getPrimary().getType())
    );
}
```

## Prevention: Best Practices

1. **Wait for page load:**
   ```gherkin
   Given the user is on the practice form page
   Then the user waits for element with id "firstName" to be visible
   Then generate locators for page "PracticeFormPage"
   ```

2. **Use specific page names:**
   ```gherkin
   Then generate locators for page "PracticeFormPage"  // Good
   Then generate locators for current page             // Less explicit
   ```

3. **Generate after all content loads:**
   - Not immediately after navigation
   - Wait for async content to load
   - Wait for animations to complete

4. **Monitor extraction results:**
   - Check logs for element count
   - Verify JSON has elements before running tests
   - Add assertions if needed

5. **Keep INTERACTABLE_TAGS updated:**
   - Review periodically
   - Add new framework-specific tags
   - Document why each tag is included

## Still Stuck?

If you've tried all the above and elements are still empty:

1. **Verify page structure:**
   ```javascript
   // In browser console
   document.body.innerHTML.length  // Page has content?
   document.querySelectorAll('*').length  // How many elements total?
   ```

2. **Check for iframes:**
   ```javascript
   document.querySelectorAll('iframe').length
   // Extraction doesn't look inside iframes
   ```

3. **Verify script execution:**
   ```javascript
   // Manually run extraction script in console
   // See if it returns elements
   ```

4. **Check for CORS/XSS issues:**
   - If page is cross-origin, extraction might fail
   - Check browser console for security warnings

5. **Contact support with:**
   - Page URL
   - Browser and driver version
   - Full log output
   - Screenshot showing visible elements
   - Result of manual JavaScript execution

## Reference

### Extraction Configuration

**File:** `src/main/java/com/automation/locator/extractor/LocatorExtractor.java`

**Key method:** `getExtractionScript()`

**Visibility checks:**
- `opacity === '0'` → Skip
- `visibility === 'hidden'` → Skip
- `aria-hidden === 'true'` → Skip

**Interactable tags:**
```
BUTTON, INPUT, SELECT, TEXTAREA, A, LABEL, FORM
```

### Step Configuration

**File:** `src/main/java/com/automation/steps/LocatorSteps.java`

**Page load wait:** 2 seconds (configurable)

**Log prefix:** `[LocatorGeneration]`

## Metrics

**Expected element counts:**
- Simple page: 5-15 elements
- Complex form: 20-50 elements
- SPA dashboard: 30-100+ elements

**If your page has:**
- 0 elements → Debug (see this guide)
- 1-5 elements → Check if hidden elements exist
- 5+ elements → Normal, extraction working
