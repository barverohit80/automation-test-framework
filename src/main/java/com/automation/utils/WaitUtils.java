package com.automation.utils;

import com.automation.config.EnvironmentConfig;
import com.automation.driver.DriverFactory;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@Scope("cucumber-glue")
public class WaitUtils {

    @Autowired
    private DriverFactory driverFactory;

    @Autowired
    private EnvironmentConfig config;

    private WebDriverWait getWait() {
        return new WebDriverWait(driverFactory.getDriver(),
                Duration.ofSeconds(config.getBrowser().getExplicitWaitSeconds()));
    }

    private WebDriverWait getWait(int seconds) {
        return new WebDriverWait(driverFactory.getDriver(), Duration.ofSeconds(seconds));
    }

    public WebElement waitForVisible(By locator) {
        return getWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitForClickable(By locator) {
        return getWait().until(ExpectedConditions.elementToBeClickable(locator));
    }

    public WebElement waitForPresence(By locator) {
        return getWait().until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    public List<WebElement> waitForAllVisible(By locator) {
        return getWait().until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
    }

    public boolean waitForInvisible(By locator) {
        return getWait().until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public boolean waitForUrlContains(String urlFragment) {
        return getWait().until(ExpectedConditions.urlContains(urlFragment));
    }

    public boolean waitForTitleContains(String titleFragment) {
        return getWait().until(ExpectedConditions.titleContains(titleFragment));
    }

    public WebElement waitForVisible(By locator, int timeoutSeconds) {
        return getWait(timeoutSeconds).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
}
