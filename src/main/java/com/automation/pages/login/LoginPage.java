package com.automation.pages.login;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DemoQA Login Page — https://demoqa.com/login
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class LoginPage extends BasePage {

    @FindBy(id = "userName")
    private WebElement usernameInput;

    @FindBy(id = "password")
    private WebElement passwordInput;

    @FindBy(id = "login")
    private WebElement loginButton;

    @FindBy(id = "newUser")
    private WebElement newUserButton;

    @FindBy(id = "name")
    private WebElement outputMessage;

    public void open() {
        navigateTo("/login");
    }

    public void enterUsername(String username) {
        log.info("Entering username: {}", username);
        type(usernameInput, username);
    }

    public void enterPassword(String password) {
        log.info("Entering password: ****");
        type(passwordInput, password);
    }

    public void clickLogin() {
        log.info("Clicking login button");
        scrollToElement(loginButton);
        click(loginButton);
    }

    public void clickNewUser() {
        log.info("Clicking New User button");
        scrollToElement(newUserButton);
        click(newUserButton);
    }

    public void loginAs(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }

    public String getErrorMessage() {
        return getText(outputMessage);
    }

    public boolean isLoginPageDisplayed() {
        return isDisplayed(By.id("login"));
    }

    public boolean isErrorMessageDisplayed() {
        return isDisplayed(By.id("name"));
    }
}
