package com.automation.executor;

import com.automation.AutomationApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger FULL REGRESSION in UAT across all browsers.
 *
 * Run:
 *   java -cp automation-test-framework-1.0.0.jar \
 *        com.automation.executor.RegressionTestTrigger
 */
@Slf4j
public class RegressionTestTrigger {

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║   REGRESSION TRIGGER — UAT Cross-Browser                ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        AutomationApplication.main(new String[]{
                "--env=uat",
                "--browser=chrome",
                "--tags=not @wip",
                "--threads=6",
                "--headless=true",
                "--parallel-cross-browser=true",
                "--report-dir=target/regression-reports"
        });
    }
}
