package com.automation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Binds all properties from application-{env}.yml under the "app" prefix.
 * Accessible throughout the framework via Spring DI.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class EnvironmentConfig {

    private String environment;
    private String baseUrl;
    private String apiBaseUrl;
    private BrowserConfig browser;
    private ParallelConfig parallel;
    private SeleniumGridConfig seleniumGrid;
    private Map<String, CredentialConfig> credentials;
    private TimeoutConfig timeouts;
    private ReportingConfig reporting;
    private DataConfig data;

    @Data
    public static class BrowserConfig {
        private String defaultBrowser = "chrome";
        private boolean headless = false;
        private int implicitWaitSeconds = 10;
        private int explicitWaitSeconds = 15;
        private int pageLoadTimeoutSeconds = 30;

        // Alias: YAML key is "default" but Java field can't be "default"
        public String getDefaultBrowser() {
            return defaultBrowser;
        }

        public void setDefault(String browser) {
            this.defaultBrowser = browser;
        }
    }

    @Data
    public static class ParallelConfig {
        private boolean enabled = true;
        private int threadCount = 4;
        private CrossBrowserConfig crossBrowser;

        @Data
        public static class CrossBrowserConfig {
            private boolean enabled = false;
            private List<String> browsers = List.of("chrome");
        }
    }

    @Data
    public static class SeleniumGridConfig {
        private boolean enabled = false;
        private String hubUrl;
    }

    @Data
    public static class CredentialConfig {
        private String username;
        private String password;
    }

    @Data
    public static class TimeoutConfig {
        private int shortTimeout = 5;
        private int medium = 10;
        private int longTimeout = 30;

        public void setShort(int val) { this.shortTimeout = val; }
        public void setLong(int val) { this.longTimeout = val; }
    }

    @Data
    public static class ReportingConfig {
        private boolean screenshotOnFailure = true;
        private String screenshotDir = "target/screenshots";
    }

    @Data
    public static class DataConfig {
        private long randomSeed = 0;
        private int alphanumericLength = 10;
        private int numericLength = 8;
        private String prefix = "AUTO";
    }
}
