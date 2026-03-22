package com.automation.steps;

import com.automation.config.EnvironmentConfig;
import com.automation.context.TestContext;
import com.automation.pages.login.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class LoginSteps {

    @Autowired private LoginPage loginPage;
    @Autowired private TestContext testContext;
    @Autowired private EnvironmentConfig config;

    @Given("the user is on the login page")
    public void theUserIsOnTheLoginPage() {
        loginPage.open();
        testContext.set("currentPage", "login");
    }

    @Then("the login page should be displayed")
    public void theLoginPageShouldBeDisplayed() {
        assertTrue(loginPage.isLoginPageDisplayed(), "Login page should be displayed");
    }

    @When("the user logs in with username {string} and password {string}")
    public void theUserLogsInWith(String username, String password) {
        testContext.set("loginUsername", username);
        loginPage.loginAs(username, password);
    }

    @When("the user logs in as admin")
    public void theUserLogsInAsAdmin() {
        var creds = config.getCredentials().get("admin");
        loginPage.loginAs(creds.getUsername(), creds.getPassword());
    }

    @When("the user logs in as standard user")
    public void theUserLogsInAsStandardUser() {
        var creds = config.getCredentials().get("standard");
        loginPage.loginAs(creds.getUsername(), creds.getPassword());
    }

    @When("the user enters a random username {randomAlphanumeric}")
    public void theUserEntersRandomUsername(String randomUsername) {
        testContext.set("enteredUsername", randomUsername);
        loginPage.enterUsername(randomUsername);
    }

    @When("the user enters username {string}")
    public void theUserEntersUsername(String username) {
        loginPage.enterUsername(username);
    }

    @When("the user enters password {string}")
    public void theUserEntersPassword(String password) {
        loginPage.enterPassword(password);
    }

    @When("the user clicks the login button")
    public void theUserClicksLogin() {
        loginPage.clickLogin();
    }

    @When("the user clicks new user button")
    public void theUserClicksNewUser() {
        loginPage.clickNewUser();
    }

    @Then("the login error message should be displayed")
    public void theLoginErrorShouldBeDisplayed() {
        assertTrue(loginPage.isErrorMessageDisplayed(), "Error message should be displayed");
    }

    @Then("the login error message should contain {string}")
    public void theLoginErrorShouldContain(String expected) {
        String actual = loginPage.getErrorMessage();
        assertTrue(actual.contains(expected),
                "Expected error to contain '" + expected + "' but got: " + actual);
    }
}
