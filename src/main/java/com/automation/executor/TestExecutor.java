package com.automation.executor;

import com.automation.config.EnvironmentConfig;
import com.automation.driver.DriverFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Programmatic test executor — the core engine that triggers Cucumber runs.
 *
 * Used by:
 *   1. AutomationApplication (java -jar CLI)
 *   2. Any Spring Boot trigger class (programmatic override)
 *   3. Custom runners, schedulers, REST endpoints, etc.
 *
 * Usage:
 * <pre>
 *   @Autowired TestExecutor testExecutor;
 *
 *   TestRunOptions opts = TestRunOptions.builder()
 *       .env("uat")
 *       .browser("firefox")
 *       .tags("@smoke")
 *       .headless(true)
 *       .threads(6)
 *       .build();
 *
 *   int exitCode = testExecutor.run(opts);
 * </pre>
 */
@Slf4j
@Component
public class TestExecutor {

    @Autowired
    private EnvironmentConfig config;

    @Autowired
    private DriverFactory driverFactory;

    /**
     * Execute Cucumber tests with the given options.
     * Returns 0 on success, non-zero on failures.
     */
    public int run(TestRunOptions options) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║   TestExecutor — Programmatic Cucumber Trigger          ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
        log.info("Options: {}", options);

        applyOverrides(options);
        printConfiguration(options);

        if (options.isCrossBrowser()) {
            return executeCrossBrowser(options);
        } else {
            return executeSingleBrowser(options);
        }
    }

    /**
     * Quick-run: execute with defaults from YAML config (no overrides).
     */
    public int run() {
        return run(fromConfig());
    }

    /**
     * Create TestRunOptions pre-populated from the current YAML config.
     * Useful as a starting point before applying overrides.
     */
    public TestRunOptions fromConfig() {
        return TestRunOptions.builder()
                .env(config.getEnvironment())
                .browser(config.getBrowser().getDefaultBrowser())
                .threads(config.getParallel().getThreadCount())
                .headless(config.getBrowser().isHeadless())
                .crossBrowser(config.getParallel().getCrossBrowser().isEnabled())
                .build();
    }

    // ── Single Browser Execution ─────────────────────────────────────

    private int executeSingleBrowser(TestRunOptions options) {
        log.info("▶ Single-browser execution: {}", options.getBrowser());
        driverFactory.setTargetBrowser(options.getBrowser());

        String[] cucumberArgs = buildCucumberArgs(options);
        log.info("Cucumber CLI args: {}", String.join(" ", cucumberArgs));

        byte exitStatus = io.cucumber.core.cli.Main.run(
                cucumberArgs, Thread.currentThread().getContextClassLoader());
        return exitStatus;
    }

    // ── Cross-Browser Parallel Execution ─────────────────────────────

    /**
     * Cross-browser execution using ThreadGroup-based browser isolation.
     * Each browser runs in its own ThreadGroup so Cucumber's ForkJoinPool
     * threads inherit the correct browser identity.
     */
    private int executeCrossBrowser(TestRunOptions options) {
        List<String> browsers = config.getParallel().getCrossBrowser().getBrowsers();
        int threadsPerBrowser = options.getThreads();

        log.info("▶ Cross-browser execution: {} ({} threads per browser)", browsers, threadsPerBrowser);

        ConcurrentHashMap<String, Integer> results = new ConcurrentHashMap<>();
        List<Thread> browserThreads = new ArrayList<>();

        for (String browser : browsers) {
            // Create browser-specific ThreadGroup — Cucumber's ForkJoinPool workers inherit this
            ThreadGroup browserGroup = new ThreadGroup(DriverFactory.THREAD_GROUP_PREFIX + browser);

            Thread t = new Thread(browserGroup, () -> {
                driverFactory.setTargetBrowser(browser);

                TestRunOptions perBrowser = cloneOptions(options);
                perBrowser.setBrowser(browser);
                perBrowser.setThreads(threadsPerBrowser);
                perBrowser.setReportDir(options.getReportDir() + "/" + browser);

                String[] args = buildCucumberArgs(perBrowser);
                byte exit = io.cucumber.core.cli.Main.run(
                        args, Thread.currentThread().getContextClassLoader());

                log.info("[{}] Completed — exit code: {}", browser, exit);
                results.put(browser, (int) exit);

            }, "cross-browser-" + browser);

            browserThreads.add(t);
            t.start();
        }

        // Wait for all browser threads
        for (Thread t : browserThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                log.error("Interrupted waiting for {}: {}", t.getName(), e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        int worstExit = 0;
        log.info("┌─ Cross-Browser Results ─────────────────────────────────┐");
        for (String browser : browsers) {
            int exitCode = results.getOrDefault(browser, 1);
            String status = exitCode == 0 ? "PASSED" : "FAILED";
            log.info("│  {} — {} (exit: {})", pad(browser, 12), status, exitCode);
            worstExit = Math.max(worstExit, exitCode);
        }
        log.info("└─────────────────────────────────────────────────────────┘");

        return worstExit;
    }

    // ── Build Cucumber CLI Arguments ─────────────────────────────────

    private String[] buildCucumberArgs(TestRunOptions options) {
        List<String> args = new ArrayList<>();

        String featurePath = resolveFeaturePath(options.getFeatures());
        args.add(featurePath);

        args.add("--glue");
        args.add(options.getGlue());

        String reportDir = options.getReportDir();
        new File(reportDir).mkdirs();
        args.add("--plugin"); args.add("pretty");
        args.add("--plugin"); args.add("html:" + reportDir + "/cucumber-report.html");
        args.add("--plugin"); args.add("json:" + reportDir + "/cucumber-report.json");
        args.add("--plugin"); args.add("timeline:" + reportDir + "/timeline");

        // Allure — generates JSON results for allure report generation
        args.add("--plugin"); args.add("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm");

        // Rerun file — captures failed scenario URIs for rerun
        if (options.getRerunCount() > 0) {
            args.add("--plugin");
            args.add("rerun:" + reportDir + "/rerun.txt");
        }

        if (options.getTags() != null && !options.getTags().isBlank()) {
            args.add("--tags");
            args.add(options.getTags());
        }

        if (options.getThreads() > 1) {
            args.add("--threads");
            args.add(String.valueOf(options.getThreads()));
        }

        if (options.isDryRun()) {
            args.add("--dry-run");
        }

        args.add("--monochrome");
        return args.toArray(new String[0]);
    }

    private String resolveFeaturePath(String userPath) {
        if (userPath != null && !userPath.isBlank()) {
            File f = new File(userPath);
            if (f.exists()) {
                log.info("Using external feature path: {}", f.getAbsolutePath());
                return f.getAbsolutePath();
            }
            return userPath;
        }
        return extractFeaturesFromClasspath();
    }

    private String extractFeaturesFromClasspath() {
        try {
            Path tempDir = Files.createTempDirectory("cucumber-features-");
            tempDir.toFile().deleteOnExit();

            String[] featureFiles = {
                    "login.feature", "home.feature", "text_box.feature",
                    "web_tables.feature", "buttons.feature", "practice_form.feature",
                    "alerts.feature", "book_store.feature", "progress_bar.feature",
                    "registration.feature", "search.feature"
            };
            boolean extracted = false;

            for (String name : featureFiles) {
                String resourcePath = "features/" + name;
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Path target = tempDir.resolve(name);
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                        extracted = true;
                    }
                }
            }

            if (!extracted) {
                File devFeatures = new File("src/main/resources/features");
                if (devFeatures.exists()) return devFeatures.getAbsolutePath();
                throw new RuntimeException("No feature files found on classpath or filesystem");
            }

            log.info("Features extracted to: {}", tempDir);
            return tempDir.toAbsolutePath().toString();

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract feature files from JAR", e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Apply CLI overrides via SYSTEM PROPERTIES so that Cucumber's separate
     * Spring context (created by CucumberSpringConfiguration) also picks them up.
     *
     * Spring resolves properties in this order: system props > YAML > defaults,
     * so setting system props here guarantees Cucumber's context sees the overrides.
     */
    private void applyOverrides(TestRunOptions options) {
        // Set system properties — these override application-{env}.yml values
        // in BOTH the main Spring context AND Cucumber's Spring context.
        System.setProperty("app.environment", options.getEnv());
        System.setProperty("app.browser.default", options.getBrowser());
        System.setProperty("app.browser.headless", String.valueOf(options.isHeadless()));
        System.setProperty("app.parallel.thread-count", String.valueOf(options.getThreads()));
        System.setProperty("app.parallel.cross-browser.enabled", String.valueOf(options.isCrossBrowser()));

        // Also update the current context's config (for logging, printConfiguration, etc.)
        config.setEnvironment(options.getEnv());
        config.getBrowser().setDefault(options.getBrowser());
        config.getBrowser().setHeadless(options.isHeadless());
        config.getParallel().setThreadCount(options.getThreads());
        config.getParallel().getCrossBrowser().setEnabled(options.isCrossBrowser());
    }

    private TestRunOptions cloneOptions(TestRunOptions src) {
        return TestRunOptions.builder()
                .env(src.getEnv())
                .browser(src.getBrowser())
                .tags(src.getTags())
                .threads(src.getThreads())
                .features(src.getFeatures())
                .glue(src.getGlue())
                .dryRun(src.isDryRun())
                .crossBrowser(false)
                .headless(src.isHeadless())
                .reportDir(src.getReportDir())
                .build();
    }

    private void printConfiguration(TestRunOptions opts) {
        log.info("┌─ Run Configuration ─────────────────────────────────────┐");
        log.info("│  Environment:     {}", opts.getEnv());
        log.info("│  Browser:         {}", opts.getBrowser());
        log.info("│  Tags:            {}", opts.getTags() != null ? opts.getTags() : "(all)");
        log.info("│  Threads:         {}", opts.getThreads());
        log.info("│  Features:        {}", opts.getFeatures() != null ? opts.getFeatures() : "(classpath)");
        log.info("│  Cross-browser:   {}", opts.isCrossBrowser());
        log.info("│  Headless:        {}", opts.isHeadless());
        log.info("│  Dry run:         {}", opts.isDryRun());
        log.info("│  Report dir:      {}", opts.getReportDir());
        log.info("└─────────────────────────────────────────────────────────┘");
    }

    private String pad(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private record BrowserResult(String browser, int exitCode) {}
}
