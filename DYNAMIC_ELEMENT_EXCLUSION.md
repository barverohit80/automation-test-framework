# Dynamic Element Exclusion Guide

## Overview

The locator extraction system now automatically detects and excludes **dynamically generated elements** (UUIDs, hashes, random IDs) from the locators JSON. This keeps your locators clean and focused on stable, maintainable selectors.

## Why Exclude Dynamic Elements?

Dynamic elements are problematic because:

❌ **UUID IDs**: `f47ac10b-58cc-4372-a567-0e02b2c3d479` (changes every page load)
❌ **Hash IDs**: `abc123def456789` (changes on build/deployment)
❌ **Random Strings**: `element-xyz789abc` (unpredictable)
❌ **CSS Module Classes**: `_2x1a3b__button` (hash-based, changes on build)

These have **very low confidence scores** (0.2-0.3) and are unreliable for tests.

✅ **Stable Elements**: `id="loginButton"`, `data-testid="submit"`, `aria-label="Sign In"`
✅ **These are kept** with high confidence scores (0.65-1.0)

## Configuration

### Default Exclusion Patterns

Edit `src/main/resources/application.yml`:

```yaml
app:
  locators:
    # Exclude dynamic/generated elements
    exclude-dynamic-ids: |
      ^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$,
      ^[a-f0-9]{32}$,
      ^[a-f0-9]{16}$,
      ^[a-z]+-[a-z0-9]{6,}$,
      ^(__[a-z]+-{1,2}[a-z0-9]+|_[a-z0-9]+)$

    # Require stable identifiers (id, name, data-testid, aria-label)
    require-stable-identifier: true
```

### Pattern Breakdown

| Pattern | Matches | Example | Action |
|---------|---------|---------|--------|
| `^[a-f0-9]{8}-[a-f0-9]{4}...` | UUIDs | `f47ac10b-58cc-4372-a567...` | ❌ Exclude |
| `^[a-f0-9]{32}$` | 32-char hashes | `abc123def456789...` | ❌ Exclude |
| `^[a-f0-9]{16}$` | 16-char hashes | `abc123def456` | ❌ Exclude |
| `^[a-z]+-[a-z0-9]{6,}$` | React dynamic | `input-abc123xyz` | ❌ Exclude |
| `^(__[a-z]+-...` | CSS modules | `_2x1a3b__button` | ❌ Exclude |

## Examples

### React App with Dynamic IDs

**Without Exclusion:**
```json
{
  "elementName": "input-abc123xyz",
  "id": "input-abc123xyz",        // ← Dynamic, will change!
  "primary": {
    "type": "id",
    "value": "input-abc123xyz",
    "confidence": 0.95             // ← Looks stable but isn't
  }
}
```

After re-render:
```
id changes to: input-def456uvw    // ← Primary locator breaks!
```

**With Exclusion (Recommended):**
```json
{
  "elementName": "emailInput",
  "id": "emailInput",             // ← Stable ID
  "dataTestId": "email-input",    // ← Better still!
  "primary": {
    "type": "css",
    "value": "[data-testid='email-input']",
    "confidence": 1.0              // ← Most stable
  }
}
```

### CSS Module Classes

**Without Exclusion:**
```json
{
  "elementName": "element_2x1a3b",
  "classes": "_2x1a3b__button",
  "primary": {
    "type": "css",
    "value": "._2x1a3b__button",
    "confidence": 0.80             // ← Changes on every build!
  }
}
```

**With Exclusion:**
```json
{
  "elementName": "submitButton",
  "dataTestId": "submit-btn",
  "primary": {
    "type": "css",
    "value": "[data-testid='submit-btn']",
    "confidence": 1.0
  }
}
```

## How to Use

### 1. Generate Locators (Exclusion Applied Automatically)

```bash
mvn test -Dcucumber.filter.tags="@smoke and @generate"
```

This automatically:
- ✅ Loads 4 exclusion patterns
- ✅ Detects dynamic IDs/names/classes
- ✅ Excludes them from JSON
- ✅ Logs which patterns matched

### 2. Monitor Exclusions

Check the logs:
```
[LocatorExtractor] ✓ Loaded 4 exclusion patterns for dynamic elements
[LocatorExtractor] Excluding element with dynamic ID: f47ac10b-58cc-4372-a567...
[LocatorExtractor] Excluding element with dynamic class: _2x1a3b__button
```

### 3. Customize Patterns

For a React app with custom dynamic ID format `myapp-uuid123abc`:

```yaml
app:
  locators:
    exclude-dynamic-ids: |
      ^myapp-[a-z0-9]+$,
      ^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$,
      ^[a-f0-9]{32}$,
      ^[a-f0-9]{16}$,
      ^[a-z]+-[a-z0-9]{6,}$,
      ^(__[a-z]+-{1,2}[a-z0-9]+|_[a-z0-9]+)$
```

## Advanced: Require Stable Identifiers

```yaml
app:
  locators:
    # If true: Only include elements with id, name, data-testid, or aria-label
    # If false: Include elements even if only classes/text content
    require-stable-identifier: true
```

### Example

**With `require-stable-identifier: true`:**
```html
<button class="btn btn-primary">Click Me</button>
```
❌ **Excluded** - Only has dynamic classes, no stable ID/name/data-testid

**With `require-stable-identifier: false`:**
❌ **Still excluded** - Classes match exclusion pattern

**Recommended:**
```html
<button
  data-testid="click-button"
  class="btn btn-primary"
>Click Me</button>
```
✅ **Included** - Has stable data-testid

## Best Practices for React

### ✅ Use data-testid
```jsx
<button data-testid="login-btn">Login</button>
```

**Generated:**
```json
{
  "primary": {
    "type": "css",
    "value": "[data-testid='login-btn']",
    "confidence": 1.0
  }
}
```

### ✅ Use aria-labels
```jsx
<input aria-label="Email Address" />
```

**Generated:**
```json
{
  "primary": {
    "type": "xpath",
    "value": "//*[@aria-label='Email Address']",
    "confidence": 0.85
  }
}
```

### ✅ Use stable name attributes
```jsx
<input name="userEmail" type="email" />
```

**Generated:**
```json
{
  "primary": {
    "type": "name",
    "value": "userEmail",
    "confidence": 0.90
  }
}
```

### ❌ Avoid Dynamic IDs
```jsx
// Bad - ID changes on every render
<button id={`btn-${Math.random()}`}>Click</button>

// Good - Stable ID
<button id="action-button">Click</button>
```

## Comparison: Before vs After

### Before Exclusion (65 elements)
```
root (root div)               ← Needs exclusion check
body-height                   ← Generic class
container playground-body     ← Generic classes
firstName                     ← STABLE ✓
userEmail                     ← STABLE ✓
[50 more elements...]
```

### After Exclusion (Cleaner JSON)
```
firstName                     ← STABLE ✓
userEmail                     ← STABLE ✓
gender-radio-1               ← STABLE ✓
[All elements now have good identifiers]
```

## Troubleshooting

### Pattern Not Matching?

Check regex syntax:
```bash
# Test your pattern online
https://regex101.com/
```

### Elements Still Being Included?

Verify the pattern:
1. Check element ID/name in browser DevTools
2. Copy exact value
3. Test against pattern at regex101.com
4. Update pattern if needed

### Too Many Elements Excluded?

Your pattern is too broad. Example:
```yaml
# TOO BROAD - excludes all single-word IDs
exclude-dynamic-ids: ^[a-z]+$

# BETTER - more specific
exclude-dynamic-ids: ^[a-z]+-[a-z0-9]{6,}$
```

## Summary

✅ **Dynamic Exclusion System:**
- Automatically detects UUIDs, hashes, random IDs
- Keeps JSON clean and focused on stable selectors
- Configurable patterns for custom apps
- Logs which elements are excluded and why
- Improves test reliability by avoiding fragile locators

✅ **Recommended Setup:**
- Always use `data-testid` in React
- Use `require-stable-identifier: true`
- Match patterns to your app's ID generation
- Review logs to verify exclusion is working

