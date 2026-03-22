package com.automation.steps;

import com.automation.context.TestContext;
import com.automation.pages.elements.TextBoxPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class TextBoxSteps {

    @Autowired private TextBoxPage textBoxPage;
    @Autowired private TestContext testContext;

    @Given("the user is on the text box page")
    public void theUserIsOnTheTextBoxPage() {
        textBoxPage.open();
    }

    @When("the user enters full name {string}")
    public void theUserEntersFullName(String name) {
        testContext.set("fullName", name);
        textBoxPage.enterFullName(name);
    }

    @And("the user enters email {string}")
    public void theUserEntersEmail(String email) {
        testContext.set("email", email);
        textBoxPage.enterEmail(email);
    }

    @And("the user enters current address {string}")
    public void theUserEntersCurrentAddress(String address) {
        testContext.set("currentAddress", address);
        textBoxPage.enterCurrentAddress(address);
    }

    @And("the user enters permanent address {string}")
    public void theUserEntersPermanentAddress(String address) {
        testContext.set("permanentAddress", address);
        textBoxPage.enterPermanentAddress(address);
    }

    @When("the user enters random full name {randomAlphanumeric}")
    public void theUserEntersRandomFullName(String name) {
        testContext.set("fullName", name);
        textBoxPage.enterFullName(name);
    }

    @And("the user enters random email {randomEmail}")
    public void theUserEntersRandomEmail(String email) {
        testContext.set("email", email);
        textBoxPage.enterEmail(email);
    }

    @When("the user submits the text box form")
    public void theUserSubmitsTheTextBoxForm() {
        textBoxPage.clickSubmit();
    }

    @Then("the submitted data should be displayed in the output")
    public void theSubmittedDataShouldBeDisplayed() {
        assertTrue(textBoxPage.isOutputDisplayed(), "Output section should be displayed");
    }

    @Then("the output name should contain {string}")
    public void theOutputNameShouldContain(String expected) {
        String actual = textBoxPage.getOutputName();
        assertTrue(actual.contains(expected),
                "Expected output name to contain '" + expected + "' but got: " + actual);
    }

    @Then("the output email should contain {string}")
    public void theOutputEmailShouldContain(String expected) {
        String actual = textBoxPage.getOutputEmail();
        assertTrue(actual.contains(expected),
                "Expected output email to contain '" + expected + "' but got: " + actual);
    }
}
