package com.automation.steps;

import com.automation.context.TestContext;
import com.automation.pages.elements.WebTablesPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class WebTablesSteps {

    @Autowired private WebTablesPage webTablesPage;
    @Autowired private TestContext testContext;

    @Given("the user is on the web tables page")
    public void theUserIsOnTheWebTablesPage() {
        webTablesPage.open();
    }

    @When("the user adds a new record with first name {string} last name {string} email {string} age {string} salary {string} department {string}")
    public void theUserAddsNewRecord(String firstName, String lastName, String email,
                                     String age, String salary, String department) {
        testContext.set("recordFirstName", firstName);
        webTablesPage.clickAdd();
        webTablesPage.fillRegistrationForm(firstName, lastName, email, age, salary, department);
        webTablesPage.clickSubmit();
    }

    @When("the user searches for {string} in the table")
    public void theUserSearchesInTable(String text) {
        webTablesPage.searchFor(text);
    }

    @Then("the record with {string} should be present in the table")
    public void theRecordShouldBePresent(String text) {
        assertTrue(webTablesPage.isRecordPresent(text),
                "Record containing '" + text + "' should be present in the table");
    }

    @Then("the record with {string} should not be present in the table")
    public void theRecordShouldNotBePresent(String text) {
        assertFalse(webTablesPage.isRecordPresent(text),
                "Record containing '" + text + "' should NOT be present in the table");
    }

    @When("the user deletes the record with {string}")
    public void theUserDeletesRecord(String name) {
        webTablesPage.deleteRecord(name);
    }

    @When("the user edits the record with {string}")
    public void theUserEditsRecord(String name) {
        webTablesPage.editRecord(name);
    }
}
