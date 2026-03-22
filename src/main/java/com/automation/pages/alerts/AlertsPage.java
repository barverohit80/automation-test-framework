package com.automation.pages.alerts;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * DemoQA Alerts Page — https://demoqa.com/alerts
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class AlertsPage extends BasePage {

    @FindBy(id = "alertButton")
    private WebElement alertButton;

    @FindBy(id = "timerAlertButton")
    private WebElement timerAlertButton;

    @FindBy(id = "confirmButton")
    private WebElement confirmButton;

    @FindBy(id = "promtButton")
    private WebElement promptButton;

    @FindBy(id = "confirmResult")
    private WebElement confirmResult;

    @FindBy(id = "promptResult")
    private WebElement promptResult;

    public void open() {
        navigateTo("/alerts");
    }

    public void clickAlertButton() {
        log.info("Clicking alert button");
        scrollToElement(alertButton);
        click(alertButton);
    }

    public void clickTimerAlertButton() {
        log.info("Clicking timer alert button");
        scrollToElement(timerAlertButton);
        click(timerAlertButton);
    }

    public void clickConfirmButton() {
        log.info("Clicking confirm button");
        scrollToElement(confirmButton);
        click(confirmButton);
    }

    public void clickPromptButton() {
        log.info("Clicking prompt button");
        scrollToElement(promptButton);
        click(promptButton);
    }

    public String acceptAlertAndGetText() {
        Alert alert = waitForAlert();
        String text = alert.getText();
        log.info("Alert text: {}", text);
        alert.accept();
        return text;
    }

    public String dismissAlertAndGetText() {
        Alert alert = waitForAlert();
        String text = alert.getText();
        log.info("Alert text: {}", text);
        alert.dismiss();
        return text;
    }

    public void sendTextToAlert(String text) {
        Alert alert = waitForAlert();
        log.info("Sending text to prompt: {}", text);
        alert.sendKeys(text);
        alert.accept();
    }

    public String getConfirmResult() {
        return getText(confirmResult);
    }

    public String getPromptResult() {
        return getText(promptResult);
    }

    private Alert waitForAlert() {
        // Use driverFactory directly — BasePage.getDriver() triggers PageFactory init
        // which is unnecessary here and could interfere with alert handling
        return new WebDriverWait(driverFactory.getDriver(), Duration.ofSeconds(10))
                .until(ExpectedConditions.alertIsPresent());
    }
}
