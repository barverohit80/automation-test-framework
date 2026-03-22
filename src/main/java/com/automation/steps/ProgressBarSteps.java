package com.automation.steps;

import com.automation.pages.widgets.ProgressBarPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class ProgressBarSteps {

    @Autowired private ProgressBarPage progressBarPage;

    @Given("the user is on the progress bar page")
    public void theUserIsOnTheProgressBarPage() {
        progressBarPage.open();
    }

    @When("the user starts the progress bar")
    public void theUserStartsProgressBar() {
        progressBarPage.clickStartStop();
    }

    @When("the user stops the progress bar")
    public void theUserStopsProgressBar() {
        progressBarPage.clickStartStop();
    }

    @When("the user waits for the progress bar to reach {int} percent")
    public void theUserWaitsForProgress(int percent) {
        progressBarPage.waitForProgressToReach(percent);
    }

    @Then("the progress bar value should be greater than {int}")
    public void progressBarShouldBeGreaterThan(int minValue) {
        int actual = Integer.parseInt(progressBarPage.getProgressValue());
        assertTrue(actual > minValue,
                "Progress bar value should be > " + minValue + " but was " + actual);
    }

    @Then("the reset button should be displayed")
    public void resetButtonShouldBeDisplayed() {
        assertTrue(progressBarPage.isResetButtonDisplayed(), "Reset button should be displayed");
    }
}
