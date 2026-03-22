package com.automation;

import com.automation.driver.DriverFactory;
import com.automation.executor.TestRunOptions;
import org.apache.commons.cli.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.automation.driver.DriverFactory.getThreadBrowserMap;
//--env=dev --tags=@regression --headless=false --threads=1 --rerun=2
//--env=dev --tags=@regression --parallel-cross-browser=true --browsers=chrome,firefox --threads=7 --rerun=2
/**
 * Main entry point for JAR / IntelliJ execution.
 *
 * This is a PLAIN main class — NOT a @SpringBootApplication.
 * It parses CLI arguments, sets system properties, and launches
 * Cucumber directly. The ONLY Spring context is Cucumber's own
 * (created by CucumberSpringConfiguration), which eliminates the
 * double-context / double-browser problem.
 *
 * Usage:
 *   java -jar app.jar --env=dev --tags=@smoke --browser=chrome --headless=false --threads=1
 *
 * IntelliJ Run Configuration:
 *   Main class:  com.automation.AutomationApplication
 *   Program args: --env=dev --tags=@smoke --headless=false --threads=1
 *
 * Cross-Browser Threading Model:
 *   --threads=7 --browsers=chrome,firefox,edge
 *     → 7 threads distributed across 3 browsers = ~2 threads per browser
 *     → Each browser runs in its own ThreadGroup ("browser-chrome", etc.)
 *     → Cucumber's ForkJoinPool threads inherit the ThreadGroup
 *     → DriverFactory.getTargetBrowser() resolves browser from ThreadGroup name
 *     → 3 browsers run in PARALLEL, each with ~2 scenario threads
 */
public class AutomationApplication {

    public static void main(String[] args) {
        TestRunOptions options = parseArgs(args);

        // ── Set system properties so Cucumber's Spring context picks them up ──
        System.setProperty("spring.profiles.active", options.getEnv());
        System.setProperty("app.environment", options.getEnv());
        System.setProperty("app.browser.default", options.getBrowser());
        System.setProperty("app.browser.headless", String.valueOf(options.isHeadless()));
        System.setProperty("app.parallel.thread-count", String.valueOf(options.getThreads()));
        System.setProperty("app.parallel.cross-browser.enabled", String.valueOf(options.isCrossBrowser()));

        // ── Print banner ──
        String browserDisplay = options.isCrossBrowser()
                ? String.join(", ", options.getCrossBrowserList())
                : options.getBrowser();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║   Selenium Cucumber Spring Boot — Test Automation       ║");
        System.out.println("║   Environment: " + pad(options.getEnv(), 41) + "║");
        System.out.println("║   Browser:     " + pad(browserDisplay, 40) + "║");
        System.out.println("║   Tags:        " + pad(options.getTags() != null ? options.getTags() : "(all)", 40) + "║");
        System.out.println("║   Threads:     " + pad(String.valueOf(options.getThreads()), 40) + "║");
        System.out.println("║   Headless:    " + pad(String.valueOf(options.isHeadless()), 40) + "║");
        System.out.println("║   Cross-Brow:  " + pad(String.valueOf(options.isCrossBrowser()), 40) + "║");
        System.out.println("║   Rerun:       " + pad(options.getRerunCount() > 0 ? options.getRerunCount() + " attempt(s)" : "disabled", 40) + "║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // ── Preserve Allure history & clean stale results ──
        preserveAllureHistory();

        // ── Execute ──
        int exitStatus;
        if (options.isCrossBrowser()) {
            exitStatus = executeCrossBrowser(options);
        } else {
            exitStatus = executeSingleBrowser(options);
        }

        // Immediately close all browsers after execution completes
        DriverFactory.quitAllDrivers();

        // Post-process Allure results: rewrite labels for browser-segregated report
        postProcessAllureResults();

        // Write Allure environment info and generate single-page report
        writeAllureEnvironment(options);
        generateAllureReport();

        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("  EXECUTION COMPLETE — Exit Code: " +
                (exitStatus == 0 ? "0 (SUCCESS)" : exitStatus + " (FAILURES)"));
        System.out.println("════════════════════════════════════════════════════════════");

        System.exit(exitStatus);
    }

    // ── Execution ──────────────────────────────────────────────────

    private static int executeSingleBrowser(TestRunOptions options) {
        String[] cucumberArgs = buildCucumberArgs(options);
        System.out.println("Cucumber CLI args: " + String.join(" ", cucumberArgs));

        int exitStatus = io.cucumber.core.cli.Main.run(
                cucumberArgs, Thread.currentThread().getContextClassLoader());

        // ── Rerun failed scenarios ──
        if (exitStatus != 0 && options.getRerunCount() > 0) {
            exitStatus = executeRerunAttempts(options, exitStatus);
        }

        return exitStatus;
    }

    /**
     * Rerun failed scenarios up to N times.
     * Uses Cucumber's rerun.txt file which contains URIs of failed scenarios.
     * Each attempt only re-executes the scenarios that failed in the previous attempt.
     */
    private static int executeRerunAttempts(TestRunOptions options, int previousExitStatus) {
        int maxRetries = options.getRerunCount();
        int exitStatus = previousExitStatus;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // The rerun file from the previous run
            String previousReportDir = attempt == 1
                    ? options.getReportDir()
                    : options.getReportDir() + "/rerun-" + (attempt - 1);
            String rerunFile = previousReportDir + "/rerun.txt";

            File rerunTxt = new File(rerunFile);
            if (!rerunTxt.exists() || rerunTxt.length() == 0) {
                System.out.println("✓ No failed scenarios to rerun — rerun file is empty or missing");
                return 0;
            }

            // Read the rerun file to show which scenarios will be retried
            String failedScenarios = readRerunFile(rerunTxt);
            if (failedScenarios.isBlank()) {
                System.out.println("✓ No failed scenarios to rerun");
                return 0;
            }

            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║   RERUN ATTEMPT " + attempt + " of " + maxRetries + pad("", 37 - String.valueOf(attempt).length() - String.valueOf(maxRetries).length()) + "║");
            System.out.println("║   Failed scenarios:                                      ║");
            for (String line : failedScenarios.split("\\n")) {
                if (!line.isBlank()) {
                    String trimmed = line.trim();
                    if (trimmed.length() > 54) trimmed = "..." + trimmed.substring(trimmed.length() - 51);
                    System.out.println("║     " + pad(trimmed, 51) + "║");
                }
            }
            System.out.println("╚══════════════════════════════════════════════════════════╝");

            // Build rerun-specific Cucumber args
            String rerunReportDir = options.getReportDir() + "/rerun-" + attempt;
            new File(rerunReportDir).mkdirs();

            List<String> rerunArgs = new ArrayList<>();

            // Use @rerun.txt as feature path — Cucumber reads failed scenario URIs from it
            rerunArgs.add("@" + rerunTxt.getAbsolutePath());

            rerunArgs.add("--glue");
            rerunArgs.add(options.getGlue());

            rerunArgs.add("--plugin"); rerunArgs.add("pretty");
            rerunArgs.add("--plugin"); rerunArgs.add("html:" + rerunReportDir + "/cucumber-report.html");
            rerunArgs.add("--plugin"); rerunArgs.add("json:" + rerunReportDir + "/cucumber-report.json");
            rerunArgs.add("--plugin"); rerunArgs.add("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm");

            // Write a new rerun file for THIS attempt (in case we need another retry)
            rerunArgs.add("--plugin"); rerunArgs.add("rerun:" + rerunReportDir + "/rerun.txt");

            if (options.getThreads() > 1) {
                rerunArgs.add("--threads");
                rerunArgs.add(String.valueOf(options.getThreads()));
            }

            rerunArgs.add("--monochrome");

            String[] args = rerunArgs.toArray(new String[0]);
            System.out.println("Rerun CLI args: " + String.join(" ", args));

            // Close all existing browsers before rerun
            DriverFactory.quitAllDrivers();

            exitStatus = io.cucumber.core.cli.Main.run(
                    args, Thread.currentThread().getContextClassLoader());

            if (exitStatus == 0) {
                System.out.println("✓ RERUN ATTEMPT " + attempt + " — ALL PREVIOUSLY FAILED SCENARIOS NOW PASS");
                return 0;
            }

            System.out.println("✗ RERUN ATTEMPT " + attempt + " — some scenarios still failing (exit: " + exitStatus + ")");
        }

        return exitStatus;
    }

    private static String readRerunFile(File rerunTxt) {
        try {
            return new String(java.nio.file.Files.readAllBytes(rerunTxt.toPath())).trim();
        } catch (IOException e) {
            System.err.println("Could not read rerun file: " + e.getMessage());
            return "";
        }
    }

    /**
     * Cross-browser parallel execution using ThreadGroup-based browser isolation.
     *
     * Each browser runs in its own ThreadGroup ("browser-chrome", "browser-firefox", etc.).
     * Cucumber's internal ForkJoinPool creates worker threads that INHERIT the parent
     * ThreadGroup, so DriverFactory.getTargetBrowser() can resolve the correct browser
     * from the ThreadGroup name on ANY thread — even ForkJoinPool workers where
     * InheritableThreadLocal does NOT propagate.
     *
     * Thread distribution:
     *   --threads=7 --browsers=chrome,firefox,edge
     *   → threadsPerBrowser = max(1, 7 / 3) = 2
     *   → Chrome gets 2 scenario threads, Firefox gets 2, Edge gets 2
     *   → 3 browsers run in parallel = 6 concurrent scenario threads + 3 management threads
     */
    private static int executeCrossBrowser(TestRunOptions options) {
        List<String> browsers = options.getCrossBrowserList();
        int threadsPerBrowser = options.getThreads();

        System.out.println("▶ Cross-browser execution: " + browsers);
        System.out.println("  Threads per browser: " + threadsPerBrowser);

        // Results map: browser → exit code
        ConcurrentHashMap<String, Integer> results = new ConcurrentHashMap<>();
        List<Thread> browserThreads = new ArrayList<>();

        for (String browser : browsers) {
            // Create a ThreadGroup named "browser-chrome", "browser-firefox", etc.
            // Cucumber's ForkJoinPool worker threads will inherit this ThreadGroup,
            // allowing DriverFactory to resolve the correct browser on ANY thread.
            ThreadGroup browserGroup = new ThreadGroup(DriverFactory.THREAD_GROUP_PREFIX + browser);

            Thread t = new Thread(browserGroup, () -> {
                // Also set ThreadLocal (for direct access on this thread)
                DriverFactory.setTargetBrowserForThread(browser);

                TestRunOptions perBrowser = TestRunOptions.builder()
                        .env(options.getEnv())
                        .browser(browser)
                        .tags(options.getTags())
                        .threads(threadsPerBrowser)
                        .features(options.getFeatures())
                        .glue(options.getGlue())
                        .dryRun(options.isDryRun())
                        .crossBrowser(false)
                        .headless(options.isHeadless())
                        .rerunCount(options.getRerunCount())
                        .reportDir(options.getReportDir() + "/" + browser)
                        .build();

                String[] args = buildCucumberArgs(perBrowser);
                System.out.println("[" + browser + "] Cucumber CLI args: " + String.join(" ", args));

                int exit = io.cucumber.core.cli.Main.run(
                        args, Thread.currentThread().getContextClassLoader());

                System.out.println("[" + browser + "] Initial run completed — exit code: " + exit);

                // Rerun failed scenarios for this browser
                if (exit != 0 && perBrowser.getRerunCount() > 0) {
                    exit = executeCrossBrowserRerun(perBrowser, browser, exit);
                }

                System.out.println("[" + browser + "] Final exit code: " + exit);
                results.put(browser, exit);

            }, "cross-browser-" + browser);

            browserThreads.add(t);
            t.start();
        }

        // Wait for all browser threads to complete
        for (Thread t : browserThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for " + t.getName() + ": " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        // Print results
        int worstExit = 0;
        System.out.println("┌─ Cross-Browser Results ─────────────────────────────────┐");
        for (String browser : browsers) {
            int exitCode = results.getOrDefault(browser, 1);
            String status = exitCode == 0 ? "PASSED" : "FAILED";
            System.out.println("│  " + pad(browser, 12) + " — " + status + " (exit: " + exitCode + ")");
            worstExit = Math.max(worstExit, exitCode);
        }
        System.out.println("└─────────────────────────────────────────────────────────┘");

        return worstExit;
    }

    /**
     * Rerun failed scenarios for a specific browser in cross-browser mode.
     * Runs on the same thread as the browser's initial execution, so the
     * ThreadGroup and ThreadLocal browser setting are already correct.
     */
    private static int executeCrossBrowserRerun(TestRunOptions perBrowser, String browser, int previousExit) {
        int maxRetries = perBrowser.getRerunCount();
        int exitStatus = previousExit;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String previousReportDir = attempt == 1
                    ? perBrowser.getReportDir()
                    : perBrowser.getReportDir() + "/rerun-" + (attempt - 1);
            String rerunFile = previousReportDir + "/rerun.txt";

            File rerunTxt = new File(rerunFile);
            if (!rerunTxt.exists() || rerunTxt.length() == 0) {
                System.out.println("[" + browser + "] ✓ No failed scenarios to rerun");
                return 0;
            }

            String failedScenarios = readRerunFile(rerunTxt);
            if (failedScenarios.isBlank()) {
                System.out.println("[" + browser + "] ✓ No failed scenarios to rerun");
                return 0;
            }

            System.out.println("[" + browser + "] ┌─ RERUN ATTEMPT " + attempt + " of " + maxRetries + " ─────────────────┐");
            for (String line : failedScenarios.split("\\n")) {
                if (!line.isBlank()) {
                    System.out.println("[" + browser + "] │  " + line.trim());
                }
            }
            System.out.println("[" + browser + "] └──────────────────────────────────────────┘");

            String rerunReportDir = perBrowser.getReportDir() + "/rerun-" + attempt;
            new File(rerunReportDir).mkdirs();

            List<String> rerunArgs = new ArrayList<>();
            rerunArgs.add("@" + rerunTxt.getAbsolutePath());
            rerunArgs.add("--glue");
            rerunArgs.add(perBrowser.getGlue());
            rerunArgs.add("--plugin"); rerunArgs.add("pretty");
            rerunArgs.add("--plugin"); rerunArgs.add("html:" + rerunReportDir + "/cucumber-report.html");
            rerunArgs.add("--plugin"); rerunArgs.add("json:" + rerunReportDir + "/cucumber-report.json");
            rerunArgs.add("--plugin"); rerunArgs.add("io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm");
            rerunArgs.add("--plugin"); rerunArgs.add("rerun:" + rerunReportDir + "/rerun.txt");
            if (perBrowser.getThreads() > 1) {
                rerunArgs.add("--threads");
                rerunArgs.add(String.valueOf(perBrowser.getThreads()));
            }
            rerunArgs.add("--monochrome");

            String[] args = rerunArgs.toArray(new String[0]);
            System.out.println("[" + browser + "] Rerun CLI args: " + String.join(" ", args));

            exitStatus = io.cucumber.core.cli.Main.run(
                    args, Thread.currentThread().getContextClassLoader());

            if (exitStatus == 0) {
                System.out.println("[" + browser + "] ✓ RERUN " + attempt + " — all previously failed scenarios now pass");
                return 0;
            }
            System.out.println("[" + browser + "] ✗ RERUN " + attempt + " — still failing (exit: " + exitStatus + ")");
        }
        return exitStatus;
    }

    // ── CLI Parsing ──────────────────────────────────────────────────

    private static TestRunOptions parseArgs(String[] args) {
        Options cliDef = buildCliOptions();
        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(cliDef, args);

            if (cmd.hasOption("help")) {
                new HelpFormatter().printHelp("java -jar automation-test-framework.jar", cliDef);
                System.exit(0);
            }

            return TestRunOptions.builder()
                    .env(cmd.getOptionValue("env", "dev"))
                    .browser(cmd.getOptionValue("browser", "chrome"))
                    .tags(cmd.getOptionValue("tags", null))
                    .threads(Integer.parseInt(cmd.getOptionValue("threads", "4")))
                    .features(cmd.getOptionValue("features", null))
                    .glue(cmd.getOptionValue("glue", "com.automation"))
                    .dryRun(Boolean.parseBoolean(cmd.getOptionValue("dry-run", "false")))
                    .crossBrowser(Boolean.parseBoolean(cmd.getOptionValue("parallel-cross-browser", "false")))
                    .crossBrowserList(cmd.getOptionValue("browsers", "chrome,firefox,edge"))
                    .headless(Boolean.parseBoolean(cmd.getOptionValue("headless", "false")))
                    .reportDir(cmd.getOptionValue("report-dir", "target/cucumber-reports"))
                    .rerunCount(Integer.parseInt(cmd.getOptionValue("rerun", "0")))
                    .build();

        } catch (ParseException e) {
            System.err.println("Failed to parse CLI arguments: " + e.getMessage());
            new HelpFormatter().printHelp("java -jar automation-test-framework.jar", cliDef);
            System.exit(1);
            return new TestRunOptions(); // unreachable
        }
    }

    private static Options buildCliOptions() {
        Options o = new Options();
        o.addOption(Option.builder("e").longOpt("env").hasArg()
                .desc("Environment: dev | uat").build());
        o.addOption(Option.builder("b").longOpt("browser").hasArg()
                .desc("Browser: chrome | firefox | edge").build());
        o.addOption(Option.builder("t").longOpt("tags").hasArg()
                .desc("Cucumber tags, e.g. \"@smoke and not @wip\"").build());
        o.addOption(Option.builder("n").longOpt("threads").hasArg()
                .desc("Parallel thread count (distributed across browsers in cross-browser mode)").build());
        o.addOption(Option.builder("f").longOpt("features").hasArg()
                .desc("Feature file/directory path").build());
        o.addOption(Option.builder("g").longOpt("glue").hasArg()
                .desc("Glue package").build());
        o.addOption(Option.builder().longOpt("dry-run").hasArg()
                .desc("Validate steps without browsers: true | false").build());
        o.addOption(Option.builder().longOpt("parallel-cross-browser").hasArg()
                .desc("Run all browsers in parallel: true | false").build());
        o.addOption(Option.builder().longOpt("browsers").hasArg()
                .desc("Comma-separated browsers for cross-browser run (default: chrome,firefox,edge)").build());
        o.addOption(Option.builder().longOpt("headless").hasArg()
                .desc("Run in headless mode: true | false").build());
        o.addOption(Option.builder().longOpt("report-dir").hasArg()
                .desc("Report output directory").build());
        o.addOption(Option.builder("r").longOpt("rerun").hasArg()
                .desc("Number of times to rerun failed scenarios (default: 0)").build());
        o.addOption(Option.builder("h").longOpt("help")
                .desc("Show help").build());
        return o;
    }

    // ── Cucumber Args Builder ────────────────────────────────────────

    private static String[] buildCucumberArgs(TestRunOptions options) {
        List<String> args = new ArrayList<>();

        // Feature path
        String featurePath = resolveFeaturePath(options.getFeatures());
        args.add(featurePath);

        // Glue
        args.add("--glue");
        args.add(options.getGlue());

        // Reports
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

        // Tags
        if (options.getTags() != null && !options.getTags().isBlank()) {
            args.add("--tags");
            args.add(options.getTags());
        }

        // Threads
        if (options.getThreads() > 1) {
            args.add("--threads");
            args.add(String.valueOf(options.getThreads()));
        }

        // Dry run
        if (options.isDryRun()) {
            args.add("--dry-run");
        }

        args.add("--monochrome");
        return args.toArray(new String[0]);
    }

    private static String resolveFeaturePath(String userPath) {
        if (userPath != null && !userPath.isBlank()) {
            File f = new File(userPath);
            if (f.exists()) {
                System.out.println("Using external feature path: " + f.getAbsolutePath());
                return f.getAbsolutePath();
            }
            return userPath;
        }

        // Try filesystem first (IDE / dev mode)
        File devFeatures = new File("src/main/resources/features");
        if (devFeatures.exists()) {
            return devFeatures.getAbsolutePath();
        }

        // Extract from classpath (JAR mode)
        return extractFeaturesFromClasspath();
    }

    private static String extractFeaturesFromClasspath() {
        try {
            Path tempDir = Files.createTempDirectory("cucumber-features-");
            tempDir.toFile().deleteOnExit();

            // UI feature files (root level)
            String[] uiFeatureFiles = {
                    "login.feature", "home.feature", "text_box.feature",
                    "web_tables.feature", "buttons.feature", "practice_form.feature",
                    "alerts.feature", "book_store.feature", "progress_bar.feature",
                    "registration.feature", "search.feature"
            };
            // API feature files (api/ subdirectory)
            String[] apiFeatureFiles = {
                    "api/bookstore_api.feature"
            };
            boolean extracted = false;

            for (String name : uiFeatureFiles) {
                String resourcePath = "features/" + name;
                try (InputStream is = AutomationApplication.class.getClassLoader()
                        .getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Path target = tempDir.resolve(name);
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                        extracted = true;
                    }
                }
            }
            for (String name : apiFeatureFiles) {
                String resourcePath = "features/" + name;
                try (InputStream is = AutomationApplication.class.getClassLoader()
                        .getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Path apiDir = tempDir.resolve("api");
                        Files.createDirectories(apiDir);
                        Path target = tempDir.resolve(name);
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                        extracted = true;
                    }
                }
            }

            if (!extracted) {
                throw new RuntimeException("No feature files found on classpath or filesystem");
            }

            System.out.println("Features extracted to: " + tempDir);
            return tempDir.toAbsolutePath().toString();

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract feature files from JAR", e);
        }
    }

    // ── Allure Report ─────────────────────────────────────────────

    /**
     * Post-process Allure result JSON files to inject browser-segregated labels.
     *
     * WHY THIS IS NEEDED:
     * The Allure Cucumber7 JVM plugin automatically sets parentSuite/suite labels
     * from the Cucumber feature file name. It controls the Allure lifecycle and
     * overwrites any labels set via Allure.label() or updateTestCase() in hooks.
     *
     * OUR APPROACH:
     * 1. ScenarioHooks sets Allure.parameter("Browser", "chrome/firefox/edge/API")
     *    — parameters are NOT overwritten by the plugin
     * 2. After ALL Cucumber runs complete, this method reads each *-result.json
     *    in target/allure-results/
     * 3. Finds the "Browser" parameter value
     * 4. Rewrites parentSuite = "CHROME" / "FIREFOX" / "EDGE" / "API"
     *    and keeps the original suite (feature name) as the sub-grouping
     *
     * RESULT in Allure Suites view:
     *   CHROME → DemoQA Text Box → scenarios
     *   CHROME → DemoQA Alerts   → scenarios
     *   FIREFOX → DemoQA Text Box → scenarios
     *   EDGE → DemoQA Text Box   → scenarios
     *   API → DemoQA BookStore REST API → scenarios
     */
    /**
     * Post-process Allure result JSON files to inject browser-segregated labels.
     *
     * Strategy:
     *   1. DriverFactory.initDriver() records threadName → browser in a static map
     *   2. Each Allure result JSON has a "thread" label like:
     *      "31689@host.ForkJoinPool-1-worker-3(42)"
     *      which contains the thread name (e.g. "ForkJoinPool-1-worker-3")
     *   3. We match the thread label against the threadName→browser map
     *   4. Rewrite parentSuite = "CHROME" / "FIREFOX" / "EDGE"
     *      and keep the original suite (feature name) as sub-grouping
     *
     * For single-browser runs, all threads map to the same browser.
     * For API tests (@api), no driver is created so no mapping exists —
     * those results get parentSuite = "API".
     */
    private static void postProcessAllureResults() {
        Path allureResults = Paths.get("target/allure-results");
        if (!Files.exists(allureResults)) {
            return;
        }

        // Get the thread→browser mapping from DriverFactory
        ConcurrentHashMap<String, String> threadBrowserMap = getThreadBrowserMap();
        System.out.println("Post-processing Allure results for browser segregation...");
        System.out.println("  Thread→Browser mapping: " + threadBrowserMap);

        ObjectMapper mapper = new ObjectMapper();
        int processed = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(allureResults, "*-result.json")) {
            for (Path resultFile : stream) {
                try {
                    JsonNode root = mapper.readTree(resultFile.toFile());
                    if (!(root instanceof ObjectNode rootObj)) continue;

                    // Find the "thread" label value from the result JSON
                    String threadLabel = findLabelValue(root, "thread");

                    // Resolve browser from thread label by matching against known thread names
                    String browser = resolveBrowserFromThread(threadLabel, threadBrowserMap);

                    // Get the existing suite label (feature name) to keep as sub-grouping
                    String existingSuite = findLabelValue(root, "suite");

                    // Rewrite labels
                    ArrayNode labels = (ArrayNode) root.get("labels");
                    if (labels == null) continue;

                    // Remove existing parentSuite, suite, subSuite labels
                    Iterator<JsonNode> it = labels.iterator();
                    while (it.hasNext()) {
                        JsonNode label = it.next();
                        String name = label.has("name") ? label.get("name").asText() : "";
                        if ("parentSuite".equals(name) || "suite".equals(name) || "subSuite".equals(name)) {
                            it.remove();
                        }
                    }

                    // Add browser-segregated labels
                    labels.add(mapper.createObjectNode()
                            .put("name", "parentSuite")
                            .put("value", browser.toUpperCase()));
                    labels.add(mapper.createObjectNode()
                            .put("name", "suite")
                            .put("value", existingSuite != null ? existingSuite : "Tests"));

                    // Make historyId unique per browser.
                    // Without this, the same scenario running in Chrome and Firefox has the
                    // same historyId (based on feature URI + line number). Allure treats
                    // duplicate historyIds as "retries" and only shows the latest one,
                    // effectively hiding one browser's results.
                    if (rootObj.has("historyId")) {
                        String originalHistoryId = rootObj.get("historyId").asText();
                        rootObj.put("historyId", originalHistoryId + "_" + browser);
                    }

                    // Also inject browser as a parameter so it shows in the scenario details
                    ArrayNode params = rootObj.has("parameters") && rootObj.get("parameters").isArray()
                            ? (ArrayNode) rootObj.get("parameters")
                            : mapper.createArrayNode();
                    params.add(mapper.createObjectNode()
                            .put("name", "Browser")
                            .put("value", browser));
                    rootObj.set("parameters", params);

                    // Write back
                    mapper.writerWithDefaultPrettyPrinter().writeValue(resultFile.toFile(), rootObj);
                    processed++;

                } catch (Exception e) {
                    System.err.println("  Skipping " + resultFile.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading allure-results: " + e.getMessage());
        }

        System.out.println("  Processed " + processed + " result file(s) for browser segregation");
    }

    /**
     * Resolve the browser name from an Allure thread label.
     *
     * Allure thread label format examples:
     *   "31689@host.cross-browser-chrome(15)"    — cross-browser parent thread
     *   "31689@host.ForkJoinPool-1-worker-3(42)" — Cucumber parallel worker
     *   "31689@host.cucumber-runner-1-thread-1(20)" — single-browser thread
     *
     * Resolution strategy:
     *   1. Check if thread label contains a known thread name from DriverFactory's map
     *      (DriverFactory records threadName→browser every time a driver is created)
     *   2. Check if thread label directly contains "cross-browser-chrome/firefox/edge"
     *      (cross-browser parent thread names)
     *   3. If only one browser exists in the map, all tests belong to it
     *   4. Fall back to "API" (for @api tests that never created a driver)
     */
    private static String resolveBrowserFromThread(String threadLabel,
                                                    ConcurrentHashMap<String, String> threadBrowserMap) {
        if (threadLabel == null || threadLabel.isBlank()) {
            return "API";
        }

        // 1. Match thread name from DriverFactory's map
        for (var entry : threadBrowserMap.entrySet()) {
            if (threadLabel.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 2. Direct pattern match: "cross-browser-chrome", "cross-browser-firefox", etc.
        String lower = threadLabel.toLowerCase();
        if (lower.contains("cross-browser-chrome") || lower.contains("-chrome(")) return "chrome";
        if (lower.contains("cross-browser-firefox") || lower.contains("-firefox(")) return "firefox";
        if (lower.contains("cross-browser-edge") || lower.contains("-edge(")) return "edge";

        // 3. If only one browser in the map, all tests belong to it (single-browser run)
        if (!threadBrowserMap.isEmpty()) {
            var browsers = new java.util.HashSet<>(threadBrowserMap.values());
            if (browsers.size() == 1) {
                return browsers.iterator().next();
            }
        }

        return "API";
    }

    private static String findLabelValue(JsonNode root, String labelName) {
        JsonNode labels = root.get("labels");
        if (labels != null && labels.isArray()) {
            for (JsonNode label : labels) {
                if (label.has("name") && labelName.equals(label.get("name").asText())) {
                    return label.has("value") ? label.get("value").asText() : null;
                }
            }
        }
        return null;
    }

    /**
     * Write environment.properties into allure-results/ so it shows
     * in the Allure report's Environment widget.
     */
    private static void writeAllureEnvironment(TestRunOptions options) {
        try {
            Path allureResults = Paths.get("target/allure-results");
            Files.createDirectories(allureResults);

            // In cross-browser mode, show all browsers; otherwise show the single browser
            String browserDisplay = options.isCrossBrowser()
                    ? String.join(", ", options.getCrossBrowserList())
                    : options.getBrowser();

            String content = String.join("\n",
                    "Environment=" + options.getEnv(),
                    "Browser=" + browserDisplay,
                    "Headless=" + options.isHeadless(),
                    "Threads=" + options.getThreads(),
                    "Tags=" + (options.getTags() != null ? options.getTags() : "all"),
                    "Cross-Browser=" + options.isCrossBrowser(),
                    "OS=" + System.getProperty("os.name"),
                    "Java=" + System.getProperty("java.version"),
                    ""
            );
            Files.writeString(allureResults.resolve("environment.properties"), content);
            System.out.println("Allure environment.properties written");
        } catch (IOException e) {
            System.err.println("Could not write Allure environment.properties: " + e.getMessage());
        }
    }

    /**
     * Generate a self-contained single-page Allure report.
     * Tries 'allure' CLI if available, otherwise prints instructions.
     */
    private static void generateAllureReport() {
        Path allureResults = Paths.get("target/allure-results");
        if (!Files.exists(allureResults)) {
            System.out.println("No allure-results directory found — skipping report generation");
            return;
        }

        System.out.println();
        System.out.println("┌─ Allure Report ──────────────────────────────────────────┐");

        try {
            // Check if allure CLI is available
            Process check = new ProcessBuilder("allure", "--version")
                    .redirectErrorStream(true).start();
            int checkExit = check.waitFor();

            if (checkExit == 0) {
                // Generate report
                System.out.println("│  Generating Allure report...                             │");
                Process gen = new ProcessBuilder(
                        "allure", "generate",
                        "target/allure-results",
                        "--clean",
                        "-o", "target/allure-report"
                ).redirectErrorStream(true).inheritIO().start();
                int genExit = gen.waitFor();

                if (genExit == 0) {
                    Path reportIndex = Paths.get("target/allure-report/index.html");
                    System.out.println("│  Report generated: " + pad(reportIndex.toAbsolutePath().toString(), 37) + "│");

                    // Generate single-file report
                    System.out.println("│  Generating single-page report...                        │");
                    Process singlePage = new ProcessBuilder(
                            "allure", "generate",
                            "target/allure-results",
                            "--single-file",
                            "--clean",
                            "-o", "target/allure-report-single"
                    ).redirectErrorStream(true).inheritIO().start();
                    int spExit = singlePage.waitFor();

                    if (spExit == 0) {
                        Path singleFile = Paths.get("target/allure-report-single/index.html");
                        System.out.println("│  Single-page: " + pad(singleFile.toAbsolutePath().toString(), 41) + "│");
                    }
                } else {
                    System.out.println("│  Report generation failed (exit: " + genExit + ")                   │");
                }
            } else {
                printAllureInstructions();
            }
        } catch (Exception e) {
            // allure CLI not installed
            printAllureInstructions();
        }

        System.out.println("│                                                          │");
        System.out.println("│  Allure results: target/allure-results/                   │");
        System.out.println("└──────────────────────────────────────────────────────────┘");
    }

    private static void printAllureInstructions() {
        System.out.println("│  Allure CLI not found — install to generate reports:     │");
        System.out.println("│    brew install allure    (macOS)                        │");
        System.out.println("│    scoop install allure   (Windows)                      │");
        System.out.println("│                                                          │");
        System.out.println("│  Then run manually:                                      │");
        System.out.println("│    allure serve target/allure-results                    │");
        System.out.println("│    allure generate target/allure-results --single-file   │");
    }

    /**
     * Preserve Allure history so re-runs of the same test show a history timeline
     * instead of appearing as duplicate entries in the current execution.
     *
     * Steps:
     *   1. Copy target/allure-report/history/ → target/allure-results/history/
     *      (this carries forward the trend data from the previous report)
     *   2. Delete stale *-result.json and *-container.json from target/allure-results/
     *      (so only the upcoming run's results are treated as "current execution")
     */
    private static void preserveAllureHistory() {
        Path allureResults = Paths.get("target/allure-results");
        Path reportHistory = Paths.get("target/allure-report/history");
        Path resultsHistory = allureResults.resolve("history");

        // Step 1: copy history from the last generated report into allure-results
        if (Files.exists(reportHistory)) {
            try {
                // ensure destination exists
                Files.createDirectories(resultsHistory);

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportHistory)) {
                    for (Path src : stream) {
                        if (Files.isRegularFile(src)) {
                            Files.copy(src, resultsHistory.resolve(src.getFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                System.out.println("✓ Allure history preserved from previous report");
            } catch (IOException e) {
                System.err.println("Could not copy Allure history: " + e.getMessage());
            }
        }

        // Step 2: delete stale result/container JSONs (keep the history/ subfolder)
        if (Files.exists(allureResults)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(allureResults,
                    p -> Files.isRegularFile(p)
                            && (p.toString().endsWith("-result.json")
                            || p.toString().endsWith("-container.json")))) {
                int deleted = 0;
                for (Path f : stream) {
                    Files.deleteIfExists(f);
                    deleted++;
                }
                if (deleted > 0) {
                    System.out.println("✓ Cleaned " + deleted + " stale Allure result file(s)");
                }
            } catch (IOException e) {
                System.err.println("Could not clean stale Allure results: " + e.getMessage());
            }
        }
    }

    private static String pad(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
