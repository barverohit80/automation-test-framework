package com.automation.driver;

import com.automation.config.EnvironmentConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe WebDriver factory.
 * Each thread gets its own WebDriver instance via ThreadLocal,
 * enabling parallel and cross-browser execution.
 *
 * Browser Resolution Strategy (in priority order):
 *   1. BROWSER_THREAD_LOCAL — set explicitly on the thread
 *   2. ThreadGroup name     — "browser-chrome" / "browser-firefox" / "browser-edge"
 *                              (Cucumber's ForkJoinPool threads inherit their parent's ThreadGroup,
 *                               so cross-browser threads propagate the browser to ALL child threads)
 *   3. Config default       — app.browser.default from YAML / system property
 *
 * Session reuse strategy:
 *   - By default, the same browser session is REUSED across scenarios
 *     running on the same thread. This avoids the overhead of launching
 *     a new browser for every scenario.
 *   - Scenarios tagged with @newbrowser get a FRESH browser session:
 *     the existing session is killed and a brand new one is created.
 *   - After a @newbrowser scenario completes, the fresh session is also
 *     killed so the next normal scenario gets the shared session back.
 *
 * Call flow (managed by ScenarioHooks):
 *   Normal scenario:    getDriver()          → reuses existing or creates shared
 *   @newbrowser:        forceNewDriver()     → kills existing + creates fresh
 *   After @newbrowser:  quitDriver()         → kills the fresh session
 *   After normal:       cleanupSession()     → clears cookies/state, keeps session alive
 */
@Slf4j
@Component
public class DriverFactory {

    /** Prefix used for browser-specific ThreadGroups in cross-browser mode */
    public static final String THREAD_GROUP_PREFIX = "browser-";

    private static final ThreadLocal<WebDriver> DRIVER_THREAD_LOCAL = new ThreadLocal<>();
    /** InheritableThreadLocal so directly spawned child threads inherit the browser */
    private static final InheritableThreadLocal<String> BROWSER_THREAD_LOCAL = new InheritableThreadLocal<>();
    /** Tracks whether the current session was created by @newbrowser (must be killed after) */
    private static final ThreadLocal<Boolean> FORCE_NEW_SESSION = ThreadLocal.withInitial(() -> false);
    /** Tracks ALL active drivers across threads for shutdown cleanup */
    private static final Set<WebDriver> ALL_DRIVERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /**
     * Maps thread names to browser names. Populated in initDriver().
     * Used by Allure post-processor to match the "thread" label in result JSONs
     * to the actual browser that ran the test.
     */
    private static final ConcurrentHashMap<String, String> THREAD_BROWSER_MAP = new ConcurrentHashMap<>();

    static {
        // Shutdown hook to quit ALL browsers when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (WebDriver driver : ALL_DRIVERS) {
                try {
                    driver.quit();
                } catch (Exception ignored) {}
            }
            ALL_DRIVERS.clear();
        }, "driver-shutdown-hook"));
    }

    @Autowired
    private EnvironmentConfig config;

    /**
     * Get or create a WebDriver for the current thread.
     * Reuses the existing session if one is alive.
     */
    public WebDriver getDriver() {
        WebDriver existing = DRIVER_THREAD_LOCAL.get();
        if (existing != null) {
            return existing;
        }
        initDriver(getTargetBrowser());
        return DRIVER_THREAD_LOCAL.get();
    }

    /**
     * Initialize a driver for a specific browser (used in cross-browser parallel runs).
     */
    public WebDriver getDriver(String browser) {
        if (DRIVER_THREAD_LOCAL.get() == null) {
            initDriver(browser);
        }
        return DRIVER_THREAD_LOCAL.get();
    }

    /**
     * Force a brand-new browser session — kills any existing session first.
     * Called by ScenarioHooks when a scenario has the @newbrowser tag.
     */
    public WebDriver forceNewDriver() {
        log.info("[Thread-{}] @newbrowser → forcing fresh browser session",
                Thread.currentThread().getId());

        // Kill existing session if any
        WebDriver existing = DRIVER_THREAD_LOCAL.get();
        if (existing != null) {
            try {
                existing.quit();
            } catch (Exception e) {
                log.debug("Ignoring error while quitting old session: {}", e.getMessage());
            }
            DRIVER_THREAD_LOCAL.remove();
        }

        // Mark this as a forced new session
        FORCE_NEW_SESSION.set(true);

        // Create fresh driver
        initDriver(getTargetBrowser());
        return DRIVER_THREAD_LOCAL.get();
    }

    /**
     * Check if the current session was created by forceNewDriver().
     */
    public boolean isForceNewSession() {
        return FORCE_NEW_SESSION.get();
    }

    /**
     * Set the target browser for the current thread before driver initialization.
     * Used by the cross-browser runner to assign browsers to threads.
     * Also works as a static call from AutomationApplication (no Spring bean needed).
     */
    public void setTargetBrowser(String browser) {
        BROWSER_THREAD_LOCAL.set(browser);
    }

    /**
     * Static version — sets the target browser on the current thread's InheritableThreadLocal.
     * Child threads (e.g., Cucumber's thread pool) will inherit this value.
     * Used by AutomationApplication for cross-browser execution.
     */
    public static void setTargetBrowserForThread(String browser) {
        BROWSER_THREAD_LOCAL.set(browser);
    }

    /**
     * Resolve the target browser for the current thread.
     *
     * Resolution order:
     *   1. BROWSER_THREAD_LOCAL — explicitly set on this thread (or inherited)
     *   2. ThreadGroup name     — "browser-xyz" → "xyz"
     *      (Cucumber's ForkJoinPool worker threads inherit their creator's ThreadGroup,
     *       so in cross-browser mode every pool thread resolves to the correct browser)
     *   3. Config default       — from YAML / system property
     */
    public String getTargetBrowser() {
        // 1. Check ThreadLocal (set directly or inherited from parent thread)
        String browser = BROWSER_THREAD_LOCAL.get();
        if (browser != null) {
            return browser;
        }

        // 2. Check ThreadGroup name — essential for cross-browser mode
        //    Cross-browser threads run inside ThreadGroup("browser-chrome"), etc.
        //    Cucumber's ForkJoinPool workers inherit this ThreadGroup, so they
        //    resolve to the correct browser even though InheritableThreadLocal
        //    does NOT propagate to ForkJoinPool threads.
        String groupName = Thread.currentThread().getThreadGroup().getName();
        if (groupName.startsWith(THREAD_GROUP_PREFIX)) {
            String resolved = groupName.substring(THREAD_GROUP_PREFIX.length());
            // Cache in ThreadLocal for subsequent calls on this thread
            BROWSER_THREAD_LOCAL.set(resolved);
            log.debug("[Thread-{}] Resolved browser '{}' from ThreadGroup '{}'",
                    Thread.currentThread().getId(), resolved, groupName);
            return resolved;
        }

        // 3. Fall back to config default
        return config.getBrowser().getDefaultBrowser();
    }

    /**
     * Detect the ACTUAL browser from the running WebDriver instance.
     * Uses instanceof check — 100% reliable regardless of ThreadLocal or ThreadGroup state.
     * Used by ScenarioHooks for Allure report labeling.
     *
     * @return "chrome", "firefox", "edge", or "unknown"
     */
    public String getActualBrowserName() {
        WebDriver driver = DRIVER_THREAD_LOCAL.get();
        if (driver == null) {
            return getTargetBrowser(); // fallback if no driver yet
        }
        if (driver instanceof ChromeDriver) return "chrome";
        if (driver instanceof FirefoxDriver) return "firefox";
        if (driver instanceof EdgeDriver) return "edge";
        if (driver instanceof RemoteWebDriver remoteDriver) {
            // For Selenium Grid — check the browser name from capabilities
            String browserName = remoteDriver.getCapabilities().getBrowserName();
            if (browserName != null && !browserName.isBlank()) {
                return browserName.toLowerCase();
            }
        }
        return "unknown";
    }

    private void initDriver(String browser) {
        log.info("[Thread-{}] Initializing {} driver (headless={}) [ThreadGroup={}]",
                Thread.currentThread().getId(), browser, config.getBrowser().isHeadless(),
                Thread.currentThread().getThreadGroup().getName());

        WebDriver driver;

        if (config.getSeleniumGrid().isEnabled()) {
            driver = createRemoteDriver(browser);
        } else {
            driver = createLocalDriver(browser);
        }

        configureTimeouts(driver);
        driver.manage().window().maximize();
        DRIVER_THREAD_LOCAL.set(driver);
        BROWSER_THREAD_LOCAL.set(browser);   // pin browser name on THIS thread
        ALL_DRIVERS.add(driver);

        // Record thread→browser mapping for Allure post-processing
        THREAD_BROWSER_MAP.put(Thread.currentThread().getName(), browser);

        log.info("[Thread-{}] {} driver ready — session started",
                Thread.currentThread().getId(), browser);
    }

    private WebDriver createLocalDriver(String browser) {
        // Selenium 4.18+ has built-in SeleniumManager that auto-downloads drivers.
        // No need for WebDriverManager.setup() calls.
        return switch (browser.toLowerCase()) {
            case "chrome" -> {
                ChromeOptions options = new ChromeOptions();
                if (config.getBrowser().isHeadless()) {
                    options.addArguments("--headless=new");
                }
                options.addArguments("--no-sandbox", "--disable-dev-shm-usage",
                        "--disable-gpu", "--window-size=1920,1080");
                yield new ChromeDriver(options);
            }
            case "firefox" -> {
                FirefoxOptions options = new FirefoxOptions();
                if (config.getBrowser().isHeadless()) {
                    options.addArguments("--headless");
                }
                yield new FirefoxDriver(options);
            }
            case "edge" -> {
                // Use WebDriverManager for Edge — Selenium's built-in SeleniumManager
                // fails because msedgedriver.azureedge.net is often unreachable.
                WebDriverManager.edgedriver().setup();
                EdgeOptions options = new EdgeOptions();
                if (config.getBrowser().isHeadless()) {
                    options.addArguments("--headless=new");
                }
                // Explicitly set Edge binary path for macOS
                String edgePath = findEdgeBinary();
                if (edgePath != null) {
                    options.setBinary(edgePath);
                }
                yield new EdgeDriver(options);
            }
            default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
        };
    }

    private WebDriver createRemoteDriver(String browser) {
        try {
            URL gridUrl = new URL(config.getSeleniumGrid().getHubUrl());
            return switch (browser.toLowerCase()) {
                case "chrome" -> {
                    ChromeOptions opts = new ChromeOptions();
                    if (config.getBrowser().isHeadless()) opts.addArguments("--headless=new");
                    yield new RemoteWebDriver(gridUrl, opts);
                }
                case "firefox" -> {
                    FirefoxOptions opts = new FirefoxOptions();
                    if (config.getBrowser().isHeadless()) opts.addArguments("--headless");
                    yield new RemoteWebDriver(gridUrl, opts);
                }
                case "edge" -> {
                    EdgeOptions opts = new EdgeOptions();
                    if (config.getBrowser().isHeadless()) opts.addArguments("--headless=new");
                    yield new RemoteWebDriver(gridUrl, opts);
                }
                default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
            };
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Selenium Grid URL: " + config.getSeleniumGrid().getHubUrl(), e);
        }
    }

    /**
     * Find the Edge browser binary on the local system.
     * Returns null if not found (let Selenium try to auto-detect).
     */
    private static String findEdgeBinary() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] paths;
        if (os.contains("mac")) {
            paths = new String[]{
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
            };
        } else if (os.contains("win")) {
            paths = new String[]{
                    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
            };
        } else {
            paths = new String[]{
                    "/usr/bin/microsoft-edge",
                    "/usr/bin/microsoft-edge-stable"
            };
        }
        for (String path : paths) {
            if (new java.io.File(path).exists()) {
                log.info("Found Edge binary at: {}", path);
                return path;
            }
        }
        log.warn("Edge binary not found at known paths — letting Selenium auto-detect");
        return null;
    }

    private void configureTimeouts(WebDriver driver) {
        driver.manage().timeouts()
                .implicitlyWait(Duration.ofSeconds(config.getBrowser().getImplicitWaitSeconds()))
                .pageLoadTimeout(Duration.ofSeconds(config.getBrowser().getPageLoadTimeoutSeconds()));
    }

    /**
     * Quit driver and clean up ALL ThreadLocals for the current thread.
     * Used after @newbrowser scenarios and at the very end of execution.
     */
    public void quitDriver() {
        WebDriver driver = DRIVER_THREAD_LOCAL.get();
        if (driver != null) {
            log.info("[Thread-{}] Quitting driver", Thread.currentThread().getId());
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Error quitting driver: {}", e.getMessage());
            } finally {
                ALL_DRIVERS.remove(driver);
                DRIVER_THREAD_LOCAL.remove();
                FORCE_NEW_SESSION.remove();
            }
        }
    }

    /**
     * Quit driver AND clear the browser ThreadLocal.
     * Called at the very end of a thread's lifecycle (e.g., shutdown hook).
     */
    public void quitDriverFully() {
        quitDriver();
        BROWSER_THREAD_LOCAL.remove();
    }

    /**
     * Clean up the session WITHOUT killing the browser.
     * Called after normal (non-@newbrowser) scenarios to reset state
     * while keeping the browser alive for the next scenario.
     */
    public void cleanupSession() {
        WebDriver driver = DRIVER_THREAD_LOCAL.get();
        if (driver != null) {
            try {
                driver.manage().deleteAllCookies();
                // Navigate to blank page to clear any JS state
                driver.get("about:blank");
                log.info("[Thread-{}] Session cleaned (cookies cleared, navigated to blank) — driver kept alive",
                        Thread.currentThread().getId());
            } catch (Exception e) {
                log.warn("[Thread-{}] Session cleanup failed, quitting browser: {}",
                        Thread.currentThread().getId(), e.getMessage());
                try { driver.quit(); } catch (Exception ignored) {}
                ALL_DRIVERS.remove(driver);
                DRIVER_THREAD_LOCAL.remove();
            }
        }
    }

    /**
     * Reset the force-new flag after a @newbrowser scenario's teardown.
     */
    public void clearForceNewFlag() {
        FORCE_NEW_SESSION.remove();
    }

    /**
     * Check if a driver exists for the current thread.
     */
    public boolean hasDriver() {
        return DRIVER_THREAD_LOCAL.get() != null;
    }

    /**
     * Get the thread→browser mapping for Allure post-processing.
     * Keys are thread names, values are browser names.
     */
    public static ConcurrentHashMap<String, String> getThreadBrowserMap() {
        return THREAD_BROWSER_MAP;
    }

    /**
     * Quit ALL active drivers across all threads.
     * Called after Cucumber execution completes to immediately close all browsers.
     */
    public static void quitAllDrivers() {
        log.info("Quitting all {} active browser(s)", ALL_DRIVERS.size());
        for (WebDriver driver : ALL_DRIVERS) {
            try {
                driver.quit();
            } catch (Exception ignored) {}
        }
        ALL_DRIVERS.clear();
    }
}
