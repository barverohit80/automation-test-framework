package com.automation;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Bridges Cucumber with Spring Boot.
 *
 * This is the ONLY Spring context in the application.
 * AutomationApplication is a plain main class (no @SpringBootApplication),
 * so there is no second context — no duplicate beans, no duplicate browsers.
 *
 * The inner CucumberConfig class acts as the Spring Boot entry point
 * for Cucumber's context, scanning all beans under com.automation.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = CucumberSpringConfiguration.CucumberConfig.class)
public class CucumberSpringConfiguration {

    @SpringBootApplication(scanBasePackages = "com.automation")
    static class CucumberConfig {
        // Single Spring Boot entry point for the Cucumber context.
        // Scans all beans: DriverFactory, pages, steps, hooks, utils, etc.
    }
}
