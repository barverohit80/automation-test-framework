package com.automation.executor;

import com.automation.AutomationApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger ONLY @smoke tests in headless Chrome.
 *
 * This is a thin wrapper that delegates to AutomationApplication
 * with hardcoded arguments. Run it directly from your IDE.
 *
 * Run:
 *   java -cp automation-test-framework-1.0.0.jar \
 *        com.automation.executor.SmokeTestTrigger
 *
 * Or in your IDE: right-click → Run 'SmokeTestTrigger'
 */
@Slf4j
public class SmokeTestTrigger {

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║   SMOKE TEST TRIGGER — Hardcoded Configuration          ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        AutomationApplication.main(new String[]{
                "--env=dev",
                "--browser=chrome",
                "--tags=@smoke",
                "--threads=4",
                "--headless=true",
                "--report-dir=target/smoke-reports"
        });
    }
}
