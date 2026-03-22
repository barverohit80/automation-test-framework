package com.automation.steps;

import com.automation.context.TestContext;
import com.automation.pages.forms.PracticeFormPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class PracticeFormSteps {

    @Autowired private PracticeFormPage practiceFormPage;
    @Autowired private TestContext testContext;

    @Given("the user is on the practice form page")
    public void theUserIsOnThePracticeFormPage() {
        practiceFormPage.open();
    }

    @When("the user fills in the practice form with first name {string} last name {string} email {string} gender {string} mobile {string}")
    public void theUserFillsInForm(String firstName, String lastName, String email,
                                   String gender, String mobile) {
        testContext.set("formFirstName", firstName);
        testContext.set("formLastName", lastName);
        practiceFormPage.enterFirstName(firstName);
        practiceFormPage.enterLastName(lastName);
        practiceFormPage.enterEmail(email);
        practiceFormPage.selectGender(gender);
        practiceFormPage.enterMobileNumber(mobile);
    }

    @And("the user selects hobby {string}")
    public void theUserSelectsHobby(String hobby) {
        practiceFormPage.selectHobby(hobby);
    }

    @And("the user enters subject {string}")
    public void theUserEntersSubject(String subject) {
        practiceFormPage.enterSubject(subject);
    }

    @And("the user enters form address {string}")
    public void theUserEntersFormAddress(String address) {
        practiceFormPage.enterCurrentAddress(address);
    }

    @When("the user submits the practice form")
    public void theUserSubmitsThePracticeForm() {
        practiceFormPage.clickSubmit();
    }

    @Then("the confirmation modal should be displayed")
    public void theConfirmationModalShouldBeDisplayed() {
        assertTrue(practiceFormPage.isConfirmationModalDisplayed(),
                "Confirmation modal should be displayed");
    }

    @Then("the confirmation modal title should be {string}")
    public void theModalTitleShouldBe(String expected) {
        assertEquals(expected, practiceFormPage.getConfirmationModalTitle());
    }

    @Then("the confirmation modal should contain {string}")
    public void theModalShouldContain(String expected) {
        String body = practiceFormPage.getConfirmationModalBody();
        assertTrue(body.contains(expected),
                "Modal body should contain '" + expected + "' but got: " + body);
    }
}
