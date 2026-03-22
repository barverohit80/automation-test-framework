# Selenium Cucumber Spring Boot - Test Automation Framework

A production-ready test automation framework built with **Selenium 4**, **Cucumber 7**, and **Spring Boot 3** targeting [https://demoqa.com](https://demoqa.com). Supports parallel execution, cross-browser testing, REST API testing, session reuse, failed test reruns, and browser-segregated Allure reporting.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Features](#features)
- [Getting Started](#getting-started)
- [CLI Options](#cli-options)
- [Running Tests](#running-tests)
- [Cross-Browser Execution](#cross-browser-execution)
- [Session Reuse Strategy](#session-reuse-strategy)
- [REST API Testing](#rest-api-testing)
- [Rerun Failed Tests](#rerun-failed-tests)
- [Allure Reporting](#allure-reporting)
- [Page Objects](#page-objects)
- [Feature Files](#feature-files)
- [Environment Configuration](#environment-configuration)

---

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Language |
| Spring Boot | 3.2.4 | Dependency injection, configuration |
| Selenium | 4.18.1 | Browser automation |
| Cucumber | 7.15.0 | BDD framework |
| JUnit 5 | 5.10.2 | Assertions |
| Allure | 2.25.0 | Test reporting |
| WebDriverManager | 5.7.0 | Driver binary management |
| RestTemplate | (Spring) | REST API testing |
| Lombok | 1.18.36 | Boilerplate reduction |
| Commons CLI | 1.6.0 | Command-line parsing |

---

## Project Structure

```
src/main/java/com/automation/
├── AutomationApplication.java          # CLI entry point (plain Java, NOT @SpringBootApplication)
├── CucumberSpringConfiguration.java    # Single Spring context for Cucumber
│
├── api/                                # REST API testing layer
│   ├── client/
│   │   ├── BaseApiClient.java          #   Abstract base: GET, POST, DELETE + error handling
│   │   └── BookStoreApiClient.java     #   DemoQA BookStore/Account API methods
│   ├── config/
│   │   └── RestTemplateConfig.java     #   @Bean RestTemplate with logging interceptor
│   └── model/
│       ├── Book.java                   #   Book POJO
│       ├── BooksResponse.java          #   { "books": [...] } wrapper
│       ├── TokenResponse.java          #   Token generation response
│       └── UserRequest.java            #   POST body for user/token endpoints
│
├── config/
│   ├── EnvironmentConfig.java          # @ConfigurationProperties (app.*)
│   └── SpringConfig.java              # Bean registration
│
├── context/
│   ├── TestContext.java                # Thread-safe scenario data store
│   └── ScenarioContext.java           # Scenario metadata holder
│
├── driver/
│   └── DriverFactory.java             # Thread-safe WebDriver factory
│                                      #   ThreadLocal + ThreadGroup browser resolution
│                                      #   Session reuse, @newbrowser support
│                                      #   Static THREAD_BROWSER_MAP for Allure
│
├── executor/
│   ├── TestExecutor.java              # Programmatic Cucumber trigger
│   └── TestRunOptions.java            # CLI argument POJO with builder
│
├── hooks/
│   └── ScenarioHooks.java             # @Before/@After: driver lifecycle, screenshots, Allure
│
├── pages/                             # Page Object Model
│   ├── base/BasePage.java             #   Abstract base (PageFactory, navigation, waits)
│   ├── home/HomePage.java
│   ├── login/LoginPage.java
│   ├── elements/TextBoxPage.java
│   ├── elements/ButtonsPage.java
│   ├── elements/WebTablesPage.java
│   ├── forms/PracticeFormPage.java
│   ├── alerts/AlertsPage.java
│   ├── bookstore/BookStorePage.java
│   └── widgets/ProgressBarPage.java
│
├── steps/                             # Cucumber step definitions
│   ├── CommonSteps.java               #   Reusable URL/title assertions
│   ├── HomeSteps.java
│   ├── LoginSteps.java
│   ├── TextBoxSteps.java
│   ├── ButtonsSteps.java
│   ├── WebTablesSteps.java
│   ├── PracticeFormSteps.java
│   ├── AlertsSteps.java
│   ├── BookStoreSteps.java
│   ├── ProgressBarSteps.java
│   ├── RegistrationSteps.java
│   └── api/BookStoreApiSteps.java     #   REST API step definitions
│
├── paramtypes/
│   └── CustomParameterTypes.java      # randomAlphanumeric, randomEmail, etc.
│
└── utils/
    ├── ScreenshotUtils.java
    ├── RandomDataGenerator.java
    └── WaitUtils.java

src/main/resources/
├── application.yml                     # Base config
├── application-dev.yml                 # Dev environment
├── application-uat.yml                 # UAT environment
├── allure.properties
├── logback.xml
└── features/
    ├── home.feature
    ├── login.feature
    ├── text_box.feature
    ├── buttons.feature
    ├── web_tables.feature
    ├── practice_form.feature
    ├── alerts.feature
    ├── book_store.feature
    ├── progress_bar.feature
    ├── registration.feature
    ├── search.feature
    └── api/
        └── bookstore_api.feature       # REST API tests
```

---

## Features

| Feature | Description |
|---------|-------------|
| **Parallel Execution** | Multi-threaded scenario execution via Cucumber's `--threads` |
| **Cross-Browser Testing** | Chrome, Firefox, Edge running simultaneously with ThreadGroup isolation |
| **Session Reuse** | One browser per thread, reused across scenarios (no restart overhead) |
| **@newbrowser Tag** | Force fresh browser session for specific scenarios |
| **REST API Testing** | RestTemplate-based API tests with `@api` tag (no browser needed) |
| **Rerun Failed Tests** | Automatic retry of failed scenarios up to N times (`--rerun=N`) |
| **Allure Reporting** | Browser-segregated reports with screenshots on every scenario |
| **Fat JAR Support** | Build and run as executable JAR via Spring Boot Maven plugin |
| **Environment Profiles** | Dev/UAT configs with YAML property binding |
| **Random Data Generation** | Built-in generators for names, emails, phones, UUIDs |
| **Thread-Safe Design** | ThreadLocal drivers, ConcurrentHashMap context, cucumber-glue scoping |

---

## Getting Started

### Prerequisites

- **Java 17** (required - Lombok is incompatible with Java 25)
- **Maven 3.8+**
- **Chrome / Firefox / Edge** browser installed
- **Allure CLI** (optional, for report generation)

### Build

```bash
mvn clean package -DskipTests
```

### Quick Run

```bash
# Run smoke tests on Chrome
java -jar target/automation-test-framework-1.0.0.jar --tags=@smoke --threads=1

# Run via Maven
mvn clean test -Dcucumber.filter.tags="@smoke"
```

---

## CLI Options

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--env` | `-e` | `dev` | Environment profile: `dev` \| `uat` |
| `--browser` | `-b` | `chrome` | Browser: `chrome` \| `firefox` \| `edge` |
| `--tags` | `-t` | (all) | Cucumber tags: `@smoke`, `@regression`, `@api` |
| `--threads` | `-n` | `4` | Parallel thread count per browser |
| `--headless` | | `false` | Run browsers in headless mode |
| `--parallel-cross-browser` | | `false` | Enable cross-browser parallel execution |
| `--browsers` | | `chrome,firefox,edge` | Comma-separated browsers for cross-browser |
| `--rerun` | `-r` | `0` | Number of retry attempts for failed scenarios |
| `--features` | `-f` | (classpath) | Path to feature files |
| `--glue` | `-g` | `com.automation` | Step definition package |
| `--dry-run` | | `false` | Validate steps without launching browsers |
| `--report-dir` | | `target/cucumber-reports` | Report output directory |
| `--help` | `-h` | | Show help |

---

## Running Tests

### Via IntelliJ

**Run Configuration:**
```
Main class:      com.automation.AutomationApplication
Program args:    --env=dev --tags=@smoke --headless=false --threads=1
Working dir:     $PROJECT_DIR$
JDK:             Java 17
```

### Via JAR

```bash
# Smoke tests
java -jar target/automation-test-framework-1.0.0.jar \
  --env=dev --tags=@smoke --browser=chrome --threads=4

# Regression on Firefox, headless
java -jar target/automation-test-framework-1.0.0.jar \
  --env=uat --tags=@regression --browser=firefox --headless=true --threads=4

# API tests only (no browser)
java -jar target/automation-test-framework-1.0.0.jar --tags=@api

# Dry run (validate steps)
java -jar target/automation-test-framework-1.0.0.jar --dry-run=true --tags=@smoke
```

### Via Maven

```bash
# All tests
mvn clean test

# Specific tags
mvn clean test -Dcucumber.filter.tags="@smoke"
mvn clean test -Dcucumber.filter.tags="@regression and not @api"

# Specific environment
mvn clean test -Puat

# Custom thread count
mvn clean test -Dthread.count=8
```

---

## Cross-Browser Execution

Run the same test suite on multiple browsers simultaneously.

```bash
java -jar target/automation-test-framework-1.0.0.jar \
  --parallel-cross-browser=true \
  --browsers=chrome,firefox,edge \
  --threads=7 \
  --tags=@smoke
```

### How It Works

```
--threads=7 --browsers=chrome,firefox,edge

Main Thread
├── ThreadGroup("browser-chrome")
│   └── Thread("cross-browser-chrome")
│       └── Cucumber Main.run() with --threads=7
│           ├── ForkJoinPool-1-worker-1  →  DriverFactory → Chrome
│           ├── ForkJoinPool-1-worker-2  →  DriverFactory → Chrome
│           └── ...
├── ThreadGroup("browser-firefox")
│   └── Thread("cross-browser-firefox")
│       └── Cucumber Main.run() with --threads=7
│           ├── ForkJoinPool-2-worker-1  →  DriverFactory → Firefox
│           └── ...
└── ThreadGroup("browser-edge")
    └── Thread("cross-browser-edge")
        └── Cucumber Main.run() with --threads=7
            └── ...
```

**Browser Resolution (DriverFactory.getTargetBrowser):**

1. Check `BROWSER_THREAD_LOCAL` (set directly on thread)
2. Check `ThreadGroup` name (`"browser-chrome"` -> `"chrome"`)
3. Fall back to config default

ForkJoinPool workers **inherit their parent's ThreadGroup**, so every worker thread resolves to the correct browser — even though `InheritableThreadLocal` doesn't propagate to ForkJoinPool threads.

---

## Session Reuse Strategy

```
Normal scenario (no @newbrowser):
  @Before  → reuse existing browser (or create one)
  @After   → clear cookies + navigate to about:blank (keep alive)

@newbrowser scenario:
  @Before  → kill existing session + launch FRESH browser
  @After   → kill the fresh session completely

Next normal scenario after @newbrowser:
  @Before  → no session exists → creates new shared session
```

This avoids browser restart overhead. A single browser handles multiple scenarios, with state cleaned between each.

---

## REST API Testing

API tests use `@api` tag — **no browser is launched**.

### Architecture

```
Feature File (@api)
    └── BookStoreApiSteps
        └── BookStoreApiClient
            └── BaseApiClient
                └── RestTemplate (Spring-managed, with logging)
```

### Example

```gherkin
@api @bookstore-api @smoke
Scenario: Get all books from the store
  When the user sends a GET request to fetch all books
  Then the API response status code should be 200
  And the response should contain a list of books
  And each book should have a title and ISBN
```

### Available API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/BookStore/v1/Books` | List all books |
| GET | `/BookStore/v1/Book?ISBN=xxx` | Get book by ISBN |
| POST | `/Account/v1/User` | Create user |
| POST | `/Account/v1/GenerateToken` | Generate auth token |
| POST | `/Account/v1/Authorized` | Check authorization |

### Run API Tests

```bash
java -jar target/automation-test-framework-1.0.0.jar --tags=@api
```

---

## Rerun Failed Tests

Automatically retry failed scenarios up to N times.

```bash
java -jar target/automation-test-framework-1.0.0.jar \
  --tags=@regression --rerun=2
```

### How It Works

1. Initial run generates `rerun.txt` with failed scenario URIs
2. Framework reads `rerun.txt` and re-executes only failed scenarios
3. Each attempt generates its own `rerun.txt` for the next retry
4. Stops immediately when all scenarios pass (exit code 0)
5. Works in both single-browser and cross-browser modes

```
target/cucumber-reports/
├── cucumber-report.html          # Initial run
├── rerun.txt                     # Failed scenarios
├── rerun-1/
│   ├── cucumber-report.html      # Retry attempt 1
│   └── rerun.txt                 # Still failing
└── rerun-2/
    └── cucumber-report.html      # Retry attempt 2
```

---

## Allure Reporting

### Browser-Segregated Reports

In cross-browser mode, the Allure report groups results by browser:

```
Suites View:
├── CHROME
│   ├── DemoQA Text Box     → scenarios with screenshots
│   ├── DemoQA Alerts       → scenarios with screenshots
│   └── ...
├── FIREFOX
│   ├── DemoQA Text Box     → scenarios with screenshots
│   └── ...
├── EDGE
│   └── ...
└── API
    └── DemoQA BookStore REST API → scenarios
```

### How Browser Segregation Works

The Allure Cucumber7 plugin controls test labels internally, so hook-based overrides don't work. Instead, the framework **post-processes Allure result JSONs** after execution:

1. `DriverFactory.initDriver()` records `threadName -> browser` in a static map
2. After all tests complete, `postProcessAllureResults()` reads each `*-result.json`
3. Matches the `thread` label against the thread-browser map
4. Rewrites `parentSuite = CHROME / FIREFOX / EDGE / API`
5. Makes `historyId` unique per browser (prevents Allure deduplication)

### Screenshots

Screenshots are captured on **every scenario** (pass and fail) and attached to both Cucumber and Allure reports.

### Generate Report

```bash
# Live server
allure serve target/allure-results

# Static HTML
allure generate target/allure-results --clean -o target/allure-report

# Single-page HTML
allure generate target/allure-results --single-file --clean -o target/allure-report-single
```

### Install Allure CLI

```bash
brew install allure        # macOS
scoop install allure       # Windows
```

---

## Page Objects

| Page | Path | Covers |
|------|------|--------|
| **BasePage** | `pages/base/` | Abstract base: navigation, clicks, typing, waits, scrolling, alerts |
| **HomePage** | `pages/home/` | DemoQA home page, category cards |
| **LoginPage** | `pages/login/` | Login form, error messages |
| **TextBoxPage** | `pages/elements/` | Text box form submission |
| **ButtonsPage** | `pages/elements/` | Double click, right click, dynamic click |
| **WebTablesPage** | `pages/elements/` | Table CRUD: add, search, delete records |
| **PracticeFormPage** | `pages/forms/` | Student registration form |
| **AlertsPage** | `pages/alerts/` | JS alerts, confirms, prompts, timer alerts |
| **BookStorePage** | `pages/bookstore/` | Book search and display |
| **ProgressBarPage** | `pages/widgets/` | Progress bar start/stop/reset |

All page objects:
- Extend `BasePage`
- Use `@Component` + `@Scope("cucumber-glue")` (one instance per scenario)
- Lazy-initialize `PageFactory` on first driver access
- Inject `DriverFactory`, `EnvironmentConfig`, `WaitUtils` via Spring

---

## Feature Files

| Feature | File | Scenarios | Tags |
|---------|------|-----------|------|
| Home Page | `home.feature` | 3 | `@home` `@smoke` |
| Login | `login.feature` | 4 | `@login` `@smoke` `@negative` `@newbrowser` |
| Text Box | `text_box.feature` | 2 | `@text-box` `@smoke` `@random-data` |
| Buttons | `buttons.feature` | 3 | `@buttons` `@smoke` |
| Web Tables | `web_tables.feature` | 4 | `@web-tables` `@smoke` `@regression` |
| Practice Form | `practice_form.feature` | 2 | `@practice-form` `@smoke` `@regression` |
| Alerts | `alerts.feature` | 5 | `@alerts` `@smoke` `@regression` |
| Book Store | `book_store.feature` | 3 | `@book-store` `@smoke` `@regression` |
| Progress Bar | `progress_bar.feature` | 2 | `@progress-bar` `@smoke` `@regression` |
| Registration | `registration.feature` | 3 | `@registration` `@newbrowser` |
| Search | `search.feature` | 2 | `@search` `@smoke` |
| **BookStore API** | `api/bookstore_api.feature` | 9 | `@api` `@smoke` `@regression` |

### Key Tags

| Tag | Purpose |
|-----|---------|
| `@smoke` | Fast smoke tests |
| `@regression` | Full regression suite (11 scenarios) |
| `@api` | REST API tests (no browser) |
| `@newbrowser` | Force fresh browser session |
| `@random-data` | Uses random data generation |

---

## Environment Configuration

### application-dev.yml

```yaml
app:
  environment: dev
  base-url: "https://demoqa.com"
  api-base-url: "https://demoqa.com"
  browser:
    default: chrome
    headless: false
    implicit-wait-seconds: 10
    explicit-wait-seconds: 15
    page-load-timeout-seconds: 30
  parallel:
    thread-count: 4
    cross-browser:
      enabled: false
      browsers: [chrome, firefox]
  credentials:
    admin:
      username: "admin_dev"
      password: "dev_admin_pass123"
```

### CLI Overrides

All YAML properties can be overridden via CLI arguments. System properties take precedence:

```
CLI --browser=firefox  →  System.setProperty("app.browser.default", "firefox")  →  overrides YAML
```

### Adding a New Environment

1. Create `application-staging.yml` in `src/main/resources/`
2. Run with `--env=staging`

---

## Reports

After execution, reports are generated at:

```
target/
├── cucumber-reports/
│   ├── cucumber-report.html        # Cucumber HTML report
│   ├── cucumber-report.json        # Cucumber JSON (for CI parsing)
│   └── timeline/                   # Parallel execution timeline
├── allure-results/                 # Raw Allure results (JSON)
├── allure-report/                  # Generated Allure HTML report
├── allure-report-single/           # Single-page Allure report
└── screenshots/                    # All scenario screenshots
```
