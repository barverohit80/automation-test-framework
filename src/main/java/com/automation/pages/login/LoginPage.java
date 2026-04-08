package com.automation.pages.login;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DemoQA Login Page — https://demoqa.com/login
 *
 * Uses self-healing resilient locators from LoginPage.json
 * Locators are ranked by confidence with primary + fallback strategy
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class LoginPage extends BasePage {

    private static final String PAGE_NAME = "LoginPage";

    public void open() {
        navigateTo("/login");
    }

    public void enterUsername(String username) {
        log.info("Entering username: {}", username);
        resilientType(PAGE_NAME, "usernameInput", username);
    }

    public void enterPassword(String password) {
        log.info("Entering password: ****");
        resilientType(PAGE_NAME, "passwordInput", password);
    }

    public void clickLogin() {
        log.info("Clicking login button");
        resilientClick(PAGE_NAME, "loginButton");
    }

    public void clickNewUser() {
        log.info("Clicking New User button");
        resilientClick(PAGE_NAME, "newUserButton");
    }

    public void loginAs(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLogin();
    }

    public String getErrorMessage() {
        return resilientGetText(PAGE_NAME, "outputMessage");
    }

    public boolean isLoginPageDisplayed() {
        return resilientIsDisplayed(PAGE_NAME, "loginButton");
    }

    public boolean isErrorMessageDisplayed() {
        return resilientIsDisplayed(PAGE_NAME, "outputMessage");
    }
}
