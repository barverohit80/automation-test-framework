package com.automation.pages.elements;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DemoQA Buttons Page — https://demoqa.com/buttons
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class ButtonsPage extends BasePage {

    @FindBy(id = "doubleClickBtn")
    private WebElement doubleClickButton;

    @FindBy(id = "rightClickBtn")
    private WebElement rightClickButton;

    // The third button has no unique id — use xpath
    private static final By DYNAMIC_CLICK_BTN = By.xpath("//button[text()='Click Me']");

    @FindBy(id = "doubleClickMessage")
    private WebElement doubleClickMessage;

    @FindBy(id = "rightClickMessage")
    private WebElement rightClickMessage;

    @FindBy(id = "dynamicClickMessage")
    private WebElement dynamicClickMessage;

    public void open() {
        navigateTo("/buttons");
    }

    public void performDoubleClick() {
        log.info("Performing double click");
        new Actions(getDriver()).doubleClick(doubleClickButton).perform();
    }

    public void performRightClick() {
        log.info("Performing right click");
        new Actions(getDriver()).contextClick(rightClickButton).perform();
    }

    public void performDynamicClick() {
        log.info("Performing dynamic (single) click");
        click(DYNAMIC_CLICK_BTN);
    }

    public String getDoubleClickMessage() {
        return getText(doubleClickMessage);
    }

    public String getRightClickMessage() {
        return getText(rightClickMessage);
    }

    public String getDynamicClickMessage() {
        return getText(dynamicClickMessage);
    }
}
