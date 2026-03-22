package com.automation.executor;

import com.automation.AutomationApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * TEMPLATE: Copy this class and customize the args array to create
 * your own trigger with hardcoded configuration.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  3 WAYS TO RUN TESTS IN THIS FRAMEWORK                            │
 * │                                                                     │
 * │  1. mvn test                                                        │
 * │     → Uses RunCucumberTest in src/test + junit-platform.properties  │
 * │     → Override via: -Dcucumber.filter.tags="@smoke" -Puat           │
 * │                                                                     │
 * │  2. java -jar app.jar --tags="@smoke" --env=uat --headless=true     │
 * │     → AutomationApplication parses CLI args into TestRunOptions     │
 * │     → Delegates to TestExecutor.run(options)                        │
 * │                                                                     │
 * │  3. Spring Boot trigger class (THIS APPROACH)                       │
 * │     → Hardcode args in code → no CLI args needed                    │
 * │     → Run from IDE or: java -cp app.jar <TriggerClassName>          │
 * │     → Perfect for CI/CD pipelines, schedulers, REST triggers        │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Run:
 *   java -cp automation-test-framework-1.0.0.jar \
 *        com.automation.executor.CustomTestTrigger
 */
@Slf4j
public class CustomTestTrigger {

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║   CUSTOM TRIGGER — Edit args below                      ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        // ══════════════════════════════════════════════════════════════
        //  CUSTOMIZE YOUR RUN HERE
        // ══════════════════════════════════════════════════════════════
        AutomationApplication.main(new String[]{
                "--env=dev",
                "--browser=chrome",
                "--tags=@smoke",
                "--threads=4",
                "--headless=true",
                "--cross-browser=false",
                "--dry-run=false",
                "--report-dir=target/custom-reports"
        });
    }
}
