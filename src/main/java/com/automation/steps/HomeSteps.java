package com.automation.steps;

import com.automation.context.TestContext;
import com.automation.pages.home.HomePage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class HomeSteps {

    @Autowired private HomePage homePage;
    @Autowired private TestContext testContext;

    @Given("the user is on the home page")
    public void theUserIsOnTheHomePage() {
        homePage.open();
    }

    @Then("the home page should be displayed")
    public void theHomePageShouldBeDisplayed() {
        assertTrue(homePage.isHomePageDisplayed(), "Home page should be displayed");
    }

    @Then("the home page should display {int} category cards")
    public void theHomePageShouldDisplayCategoryCards(int expectedCount) {
        int actualCount = homePage.getCategoryCardCount();
        assertEquals(expectedCount, actualCount,
                "Expected " + expectedCount + " category cards but found " + actualCount);
    }

    @When("the user clicks on {string} category card")
    public void theUserClicksOnCategoryCard(String cardName) {
        homePage.clickCategoryCard(cardName);
    }
}
