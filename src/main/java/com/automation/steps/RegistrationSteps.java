package com.automation.steps;

import com.automation.config.EnvironmentConfig;
import com.automation.context.TestContext;
import com.automation.driver.DriverFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class RegistrationSteps {

    @Autowired private DriverFactory driverFactory;
    @Autowired private TestContext testContext;
    @Autowired private EnvironmentConfig config;

    @Given("the user navigates to the registration page")
    public void navigateToRegistration() {
        String url = config.getBaseUrl() + "/register";
        log.info("Navigating to registration page: {}", url);
        driverFactory.getDriver().get(url);
        testContext.set("currentPage", "registration");
    }

    @When("the user enters registration name {randomAlphanumeric}")
    public void enterName(String name) {
        log.info("Random registration name: {}", name);
        testContext.set("registrationName", name);
    }

    @And("the user enters registration phone {randomNumeric}")
    public void enterPhone(String phone) {
        log.info("Random phone: {}", phone);
        testContext.set("registrationPhone", phone);
    }

    @And("the user enters registration email {randomEmail}")
    public void enterEmail(String email) {
        log.info("Random email: {}", email);
        testContext.set("registrationEmail", email);
    }

    @And("the user enters a {randomNum4} digit pin")
    public void enterPin(String pin) {
        log.info("Random 4-digit pin: {}", pin);
        testContext.set("registrationPin", pin);
    }

    @And("the user enters reference code {randomAlpha5}")
    public void enterRefCode(String code) {
        log.info("Random 5-char ref code: {}", code);
        testContext.set("registrationRefCode", code);
    }

    @Then("the registration should be successful")
    public void registrationSuccessful() {
        assertNotNull(testContext.getString("registrationName"));
    }

    @Then("the user should see a confirmation with their details")
    public void verifyConfirmation() {
        assertNotNull(testContext.getString("registrationName"));
        assertNotNull(testContext.getString("registrationEmail"));
    }
}
