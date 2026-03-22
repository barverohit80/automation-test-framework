package com.automation.pages.widgets;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * DemoQA Progress Bar Page — https://demoqa.com/progress-bar
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class ProgressBarPage extends BasePage {

    @FindBy(id = "startStopButton")
    private WebElement startStopButton;

    @FindBy(id = "resetButton")
    private WebElement resetButton;

    private static final By PROGRESS_BAR = By.cssSelector("#progressBar .progress-bar");

    public void open() {
        navigateTo("/progress-bar");
    }

    public void clickStartStop() {
        log.info("Clicking start/stop button");
        click(startStopButton);
    }

    public String getProgressValue() {
        return getDriver().findElement(PROGRESS_BAR).getAttribute("aria-valuenow");
    }

    public String getProgressText() {
        return getText(PROGRESS_BAR);
    }

    public void waitForProgressToReach(int targetPercent) {
        log.info("Waiting for progress bar to reach {}%", targetPercent);
        new WebDriverWait(getDriver(), Duration.ofSeconds(30))
                .until(driver -> {
                    String value = driver.findElement(PROGRESS_BAR).getAttribute("aria-valuenow");
                    return Integer.parseInt(value) >= targetPercent;
                });
    }

    public void clickReset() {
        log.info("Clicking reset button");
        click(resetButton);
    }

    public boolean isResetButtonDisplayed() {
        return isDisplayed(By.id("resetButton"));
    }
}
