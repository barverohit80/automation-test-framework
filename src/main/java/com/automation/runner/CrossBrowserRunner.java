package com.automation.runner;

import com.automation.config.EnvironmentConfig;
import com.automation.driver.DriverFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Cross-browser parallel runner.
 * Spawns one thread per browser from the YAML config.
 */
@Slf4j
@Component
public class CrossBrowserRunner {

    @Autowired
    private EnvironmentConfig config;

    @Autowired
    private DriverFactory driverFactory;

    public void runAcrossBrowsers(Runnable scenarioSuite) {
        List<String> browsers = config.getParallel().getCrossBrowser().getBrowsers();
        log.info("=== Cross-Browser: {} browsers -> {} ===", browsers.size(), browsers);
        ExecutorService executor = Executors.newFixedThreadPool(browsers.size());

        for (String browser : browsers) {
            executor.submit(() -> {
                Thread.currentThread().setName("browser-" + browser);
                try {
                    driverFactory.setTargetBrowser(browser);
                    scenarioSuite.run();
                } catch (Exception e) {
                    log.error("[{}] Failed: {}", browser, e.getMessage(), e);
                } finally {
                    driverFactory.quitDriver();
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
