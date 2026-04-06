package com.automation.pages.login;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DemoQA Login Page — https://demoqa.com/login
 *
 * Uses resilient (self-healing) locators loaded from resources/locators/LoginPage.json.
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class LoginPage extends BasePage {

    private static final String PAGE = "LoginPage";

    public void open() {
        navigateTo("/login");
    }

    public void enterUsername(String username) {
        log.info("Entering username: {}", username);
        resilientType(PAGE, "usernameInput", username);
    }

    public void enterPassword(String password) {
        log.info("Entering password: ****");
        resilientType(PAGE, "passwordInput", password);
    }

    public void clickLogin() {
        log.info("Clicking login button");
        resilientClick(PAGE, "loginButton");
    }

    public void clickNewUser() {
        log.info("Clicking New User button");
        resilientClick(PAGE, "newUserButton");
    }

    public void loginAs(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }

    public String getErrorMessage() {
        return resilientGetText(PAGE, "outputMessage");
    }

    public boolean isLoginPageDisplayed() {
        return resilientIsDisplayed(PAGE, "loginButton");
    }

    public boolean isErrorMessageDisplayed() {
        return resilientIsDisplayed(PAGE, "outputMessage");
    }
}
