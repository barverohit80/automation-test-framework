package com.automation.context;

import io.cucumber.java.Scenario;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Holds metadata about the currently-executing scenario.
 * Scoped to cucumber-glue so each scenario thread gets its own instance.
 */
@Slf4j
@Data
@Component
@Scope("cucumber-glue")
public class ScenarioContext {

    private Scenario scenario;
    private String scenarioName;
    private String browser;
    private LocalDateTime startTime;
    private boolean failed;

    public void init(Scenario scenario, String browser) {
        this.scenario = scenario;
        this.scenarioName = scenario.getName();
        this.browser = browser;
        this.startTime = LocalDateTime.now();
        this.failed = false;
        log.info("[Thread-{}] Scenario started: '{}' on {}",
                Thread.currentThread().getId(), scenarioName, browser);
    }

    public void attachScreenshot(byte[] screenshot) {
        if (scenario != null) {
            scenario.attach(screenshot, "image/png", scenarioName + "_failure");
        }
    }

    public void logToScenario(String message) {
        if (scenario != null) {
            scenario.log(message);
        }
    }
}
