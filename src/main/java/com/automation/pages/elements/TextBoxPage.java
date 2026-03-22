package com.automation.pages.elements;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DemoQA Text Box Page — https://demoqa.com/text-box
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class TextBoxPage extends BasePage {

    @FindBy(id = "userName")
    private WebElement fullNameInput;

    @FindBy(id = "userEmail")
    private WebElement emailInput;

    @FindBy(id = "currentAddress")
    private WebElement currentAddressInput;

    @FindBy(id = "permanentAddress")
    private WebElement permanentAddressInput;

    @FindBy(id = "submit")
    private WebElement submitButton;

    // Output fields (appear after submit)
    private static final By OUTPUT_NAME = By.cssSelector("#output #name");
    private static final By OUTPUT_EMAIL = By.cssSelector("#output #email");
    private static final By OUTPUT_CURRENT_ADDRESS = By.cssSelector("#output #currentAddress");
    private static final By OUTPUT_PERMANENT_ADDRESS = By.cssSelector("#output #permanentAddress");
    private static final By OUTPUT_CONTAINER = By.id("output");

    public void open() {
        navigateTo("/text-box");
    }

    public void enterFullName(String name) {
        log.info("Entering full name: {}", name);
        type(fullNameInput, name);
    }

    public void enterEmail(String email) {
        log.info("Entering email: {}", email);
        type(emailInput, email);
    }

    public void enterCurrentAddress(String address) {
        log.info("Entering current address: {}", address);
        type(currentAddressInput, address);
    }

    public void enterPermanentAddress(String address) {
        log.info("Entering permanent address: {}", address);
        type(permanentAddressInput, address);
    }

    public void clickSubmit() {
        log.info("Clicking submit");
        scrollToElement(submitButton);
        click(submitButton);
    }

    public boolean isOutputDisplayed() {
        return isDisplayed(OUTPUT_NAME);
    }

    public String getOutputName() {
        return getText(OUTPUT_NAME);
    }

    public String getOutputEmail() {
        return getText(OUTPUT_EMAIL);
    }

    public String getOutputCurrentAddress() {
        return getText(OUTPUT_CURRENT_ADDRESS);
    }

    public String getOutputPermanentAddress() {
        return getText(OUTPUT_PERMANENT_ADDRESS);
    }
}
