package com.automation.steps;

import com.automation.context.TestContext;
import com.automation.pages.alerts.AlertsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class AlertsSteps {

    @Autowired private AlertsPage alertsPage;
    @Autowired private TestContext testContext;

    @Given("the user is on the alerts page")
    public void theUserIsOnTheAlertsPage() {
        alertsPage.open();
    }

    @When("the user clicks the alert button")
    public void theUserClicksAlertButton() {
        alertsPage.clickAlertButton();
    }

    @When("the user clicks the timer alert button")
    public void theUserClicksTimerAlertButton() {
        alertsPage.clickTimerAlertButton();
    }

    @When("the user clicks the confirm button")
    public void theUserClicksConfirmButton() {
        alertsPage.clickConfirmButton();
    }

    @When("the user clicks the prompt button")
    public void theUserClicksPromptButton() {
        alertsPage.clickPromptButton();
    }

    @When("the user accepts the alert")
    public void theUserAcceptsAlert() {
        String text = alertsPage.acceptAlertAndGetText();
        testContext.set("alertText", text);
    }

    @When("the user dismisses the alert")
    public void theUserDismissesAlert() {
        String text = alertsPage.dismissAlertAndGetText();
        testContext.set("alertText", text);
    }

    @When("the user enters {string} in the prompt and accepts")
    public void theUserEntersInPrompt(String text) {
        alertsPage.sendTextToAlert(text);
    }

    @Then("the confirm result should contain {string}")
    public void theConfirmResultShouldContain(String expected) {
        String actual = alertsPage.getConfirmResult();
        assertTrue(actual.contains(expected),
                "Expected confirm result to contain '" + expected + "' but got: " + actual);
    }

    @Then("the prompt result should contain {string}")
    public void thePromptResultShouldContain(String expected) {
        String actual = alertsPage.getPromptResult();
        assertTrue(actual.contains(expected),
                "Expected prompt result to contain '" + expected + "' but got: " + actual);
    }
}
