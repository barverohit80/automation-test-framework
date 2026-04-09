# Debug Locator Extraction - Step-by-Step Guide

If locators are still coming back empty, use this guide to find out exactly why.

## Quick Debug Steps

### Step 1: Run Debug Extraction
```bash
mvn test -Dcucumber.filter.tags="@debug and @practice"
```

This runs `debug_extraction.feature` which:
1. Navigates to PracticeFormPage
2. Runs debug extraction that shows each element
3. Runs ultra-simple extraction with minimal filtering
4. Prints DOM diagnostic report

### Step 2: Read the Logs

#### Good Output (elements found):
```
[LocatorSteps] Step 1: Total elements in DOM: 200
[LocatorSteps] Step 2: Found 25 matching tag elements
[LocatorSteps] ✓ INCLUDE INPUT: id=firstName name=NONE text=''
[LocatorSteps] ✓ INCLUDE INPUT: id=lastName name=NONE text=''
[LocatorSteps] ❌ SKIP DIV: (DIV/SPAN not interactive)
[LocatorSteps] ════════════════════════════════════════════════════════════════
[LocatorSteps] RESULT: 12 out of 25 elements would be included
```

**Action:** Run locator generation, should work now!

#### Bad Output (all elements filtered):
```
[LocatorSteps] Step 1: Total elements in DOM: 200
[LocatorSteps] Step 2: Found 15 matching tag elements
[LocatorSteps] ❌ SKIP INPUT: (display:none)
[LocatorSteps] ❌ SKIP INPUT: (display:none)
[LocatorSteps] ❌ SKIP BUTTON: (no identifying info)
[LocatorSteps] ════════════════════════════════════════════════════════════════
[LocatorSteps] RESULT: 0 out of 15 elements would be included
[LocatorSteps] ⚠ ALL elements would be filtered out!
```

**Action:** Check the reason - see section "Understanding Filter Reasons" below

---

## Understanding Filter Reasons

### 1. "display:none"

**Meaning:** Element has CSS `display: none`

**Why:** Hidden from layout, not rendered

**Solutions:**
- Wait longer for page to render (increase sleep time)
- Elements might load dynamically
- Page might use hidden templates

**Action:** Increase wait time in LocatorSteps:
```java
// Change from 3000 to 5000
Thread.sleep(5000);
```

### 2. "visibility:hidden"

**Meaning:** Element has CSS `visibility: hidden`

**Why:** Element is hidden but still takes up space

**Solutions:**
- Same as display:none
- Page structure might hide elements initially

### 3. "opacity:0"

**Meaning:** Element has CSS `opacity: 0`

**Why:** Transparent/invisible

**Solutions:**
- Page might animate opacity on load
- Wait longer before extraction

### 4. "aria-hidden"

**Meaning:** Element has `aria-hidden="true"`

**Why:** Marked as not interactive for accessibility

**Solutions:**
- This is intentional, skip these elements
- Look for other identifying elements

### 5. "DIV/SPAN not interactive"

**Meaning:** DIV or SPAN without `role="button"` or `onclick`

**Why:** Not identified as clickable

**Solutions:**
- Check if DIV/SPAN should have role="button"
- Might need to include more DIVs/SPANs
- Or expand selector to include more tags

### 6. "no identifying info"

**Meaning:** Element has no id, name, text, or other identifiers

**Why:** Can't create meaningful locator name

**Solutions:**
- Could include these anyway (risky for flaky tests)
- Page structure might be unusual
- Check if these are dummy/placeholder elements

---

## Advanced Debugging

### Ultra-Simple Extraction

If debug extract shows 0 elements but you know page has elements:

```bash
mvn test -Dcucumber.filter.tags="@debug and @practice"
```

Check logs for "ultra-simple extract":
```
[LocatorSteps] Ultra-simple extraction (no filtering)
[LocatorSteps] Total elements extracted: 15
[LocatorSteps] Elements found:
[LocatorSteps]   - firstName: input (firstName)
[LocatorSteps]   - lastName: input (lastName)
```

**If ultra-simple has elements but debug has 0:**
- Filtering is TOO STRICT
- Need to relax visibility checks
- Or skip the problematic filter

---

## Fixing Based on What You Find

### Scenario 1: All elements are display:none

**Problem:** Page renders content after initial load

**Solution A:** Increase wait time
```java
// In LocatorSteps.java
Thread.sleep(5000);  // was 3000
```

**Solution B:** Wait for specific element to load
```gherkin
Given the user is on the practice form page
Then the user waits for element with id "firstName" to be visible
Then generate locators for page "PracticeFormPage"
```

### Scenario 2: All elements filtered because "no identifying info"

**Problem:** Many elements lack id/name/text

**Solution:** Modify extraction to include these anyway
```java
// In LocatorExtractor.getExtractionScript()
// Remove the check that skips elements without identifying info
// Keep count-based names: input_1, input_2, button_3, etc.
```

### Scenario 3: DIVs/SPANs filtered because "not interactive"

**Problem:** Page uses DIVs as buttons but doesn't mark them properly

**Solution A:** Add role="button" to DIVs (requires page changes)

**Solution B:** Expand extraction to include more DIVs
```java
// Only include DIVs/SPANs with classes or specific patterns
if (el.tagName === 'DIV' || el.tagName === 'SPAN') {
    // Include if has specific class
    if (el.className.includes('btn') || el.className.includes('button')) {
        // Include this
    }
}
```

---

## Running Full Test After Debugging

Once you understand what's being filtered, try extraction:

```bash
# Method 1: Run generation directly
mvn test -Dcucumber.filter.tags="@generate and @practice"

# Method 2: First debug, then generate
mvn test -Dcucumber.filter.tags="@debug and @practice" && \
  sleep 2 && \
  mvn test -Dcucumber.filter.tags="@generate and @practice"
```

---

## Checking Results

After running generation:

```bash
# Count elements extracted
cat src/main/resources/locators/PracticeFormPage.json | jq '.elements | length'

# View first few elements
cat src/main/resources/locators/PracticeFormPage.json | jq '.elements[] | {elementName, tag, primary}'
```

---

## Common Issues & Fixes

| Issue | Symptom | Fix |
|-------|---------|-----|
| Page still loading | `display:none` | Increase sleep time |
| No identifying info | All elements filtered | Include count-based names |
| DIVs not recognized | `DIV/SPAN not interactive` | Add role="button" or expand tags |
| Hidden containers | `display:none` on parent | Wait for container to become visible |
| Async content | `display:none` disappears after wait | Longer wait + retry |

---

## Testing Each Page

### PracticeFormPage
```bash
mvn test -Dcucumber.filter.tags="@debug and @practice"
# Should extract: firstName, lastName, email, mobile, gender, hobbies, subject, address, submit
# Expected: 20+ elements
```

### HomePage
```bash
mvn test -Dcucumber.filter.tags="@debug and @home"
# Should extract: category buttons, navigation links
# Expected: 5-10 elements
```

### LoginPage (should already work)
```bash
mvn test -Dcucumber.filter.tags="@debug and @login"
# Should extract: username, password, login button
# Expected: 3-5 elements
```

---

## If Still Stuck

1. **Post the debug output:**
   - Run `mvn test -Dcucumber.filter.tags="@debug and @practice"`
   - Copy the log section showing filtered elements
   - List the filter reasons

2. **Check page structure:**
   - Open browser DevTools
   - Run: `document.querySelectorAll('button, input, select').length`
   - Compare with debug output

3. **Check visibility:**
   - In DevTools console:
   ```javascript
   document.querySelectorAll('input').forEach(el => {
       const style = window.getComputedStyle(el);
       console.log(el.id, style.display, style.visibility, style.opacity);
   });
   ```

4. **Manual extraction verification:**
   - In DevTools console:
   ```javascript
   Array.from(document.querySelectorAll('button, input, select, textarea, a'))
       .filter(el => window.getComputedStyle(el).display !== 'none')
       .length
   ```
   - Should return the number of visible interactive elements

---

## Key Takeaways

1. **Debug extraction shows exactly what's being filtered and why**
2. **Ultra-simple extraction shows if ANY elements can be found**
3. **Logs are your friend** - they show the filtering process step-by-step
4. **Most common issue: elements still loading (display:none)**
5. **Solution: increase wait time or explicitly wait for element visibility**

Run `mvn test -Dcucumber.filter.tags="@debug and @practice"` NOW to see what's happening on your pages!
