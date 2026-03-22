package com.automation.executor;

import com.automation.AutomationApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * Trigger ONLY @newbrowser scenarios (session isolation tests)
 * in Firefox headless mode.
 *
 * Run:
 *   java -cp automation-test-framework-1.0.0.jar \
 *        com.automation.executor.NewBrowserTestTrigger
 */
@Slf4j
public class NewBrowserTestTrigger {

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║   NEW-BROWSER TRIGGER — Session Isolation Tests         ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        AutomationApplication.main(new String[]{
                "--env=dev",
                "--browser=chrome",
                "--tags=@newbrowser",
                "--threads=1",
                "--headless=false",
                "--report-dir=target/newbrowser-reports"
        });
    }
}
