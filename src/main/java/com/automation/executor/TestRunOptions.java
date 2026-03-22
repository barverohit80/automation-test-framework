package com.automation.executor;

import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration options for a Cucumber test execution run.
 *
 * Can be populated from:
 *   1. CLI args           → AutomationApplication parses --env, --tags, etc.
 *   2. Programmatic code  → Use the builder: TestRunOptions.builder().tags("@smoke").build()
 *   3. Spring Boot class  → TestExecutor.run(options) with hardcoded overrides
 *
 * Builder usage:
 * <pre>
 *   TestRunOptions opts = TestRunOptions.builder()
 *       .env("uat")
 *       .browser("firefox")
 *       .tags("@smoke and not @wip")
 *       .threads(8)
 *       .headless(true)
 *       .build();
 * </pre>
 */
@Data
public class TestRunOptions {

    private String env = "dev";
    private String browser = "chrome";
    private String tags;
    private int threads = 4;
    private String features;
    private String glue = "com.automation";
    private boolean dryRun = false;
    private boolean crossBrowser = false;
    private String crossBrowserListRaw = "chrome,firefox,edge";
    private boolean headless = false;
    private String reportDir = "target/cucumber-reports";
    private int rerunCount = 0;

    public List<String> getCrossBrowserList() {
        return Arrays.asList(crossBrowserListRaw.split(","));
    }

    /** Default constructor with YAML/config defaults. */
    public TestRunOptions() {
    }

    /** Start building options fluently. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for TestRunOptions.
     */
    public static class Builder {
        private final TestRunOptions opts = new TestRunOptions();

        public Builder env(String env) { opts.env = env; return this; }
        public Builder browser(String browser) { opts.browser = browser; return this; }
        public Builder tags(String tags) { opts.tags = tags; return this; }
        public Builder threads(int threads) { opts.threads = threads; return this; }
        public Builder features(String features) { opts.features = features; return this; }
        public Builder glue(String glue) { opts.glue = glue; return this; }
        public Builder dryRun(boolean dryRun) { opts.dryRun = dryRun; return this; }
        public Builder crossBrowser(boolean crossBrowser) { opts.crossBrowser = crossBrowser; return this; }
        public Builder crossBrowserList(String browsers) { opts.crossBrowserListRaw = browsers; return this; }
        public Builder headless(boolean headless) { opts.headless = headless; return this; }
        public Builder reportDir(String reportDir) { opts.reportDir = reportDir; return this; }
        public Builder rerunCount(int rerunCount) { opts.rerunCount = rerunCount; return this; }

        public TestRunOptions build() {
            return opts;
        }
    }

    @Override
    public String toString() {
        return String.format(
            "TestRunOptions{env='%s', browser='%s', tags='%s', threads=%d, features='%s', " +
            "headless=%s, crossBrowser=%s, dryRun=%s, rerunCount=%d, reportDir='%s'}",
            env, browser, tags, threads, features, headless, crossBrowser, dryRun, rerunCount, reportDir
        );
    }
}
