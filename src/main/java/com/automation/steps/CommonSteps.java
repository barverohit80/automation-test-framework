package com.automation.steps;

import com.automation.driver.DriverFactory;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Common step definitions reusable across features.
 */
@Slf4j
public class CommonSteps {

    @Autowired private DriverFactory driverFactory;

    @Then("the page URL should contain {string}")
    public void thePageUrlShouldContain(String expected) {
        String currentUrl = driverFactory.getDriver().getCurrentUrl();
        log.info("Current URL: {}", currentUrl);
        assertTrue(currentUrl.contains(expected),
                "Expected URL to contain '" + expected + "' but was: " + currentUrl);
    }

    @Then("the page title should contain {string}")
    public void thePageTitleShouldContain(String expected) {
        String title = driverFactory.getDriver().getTitle();
        assertTrue(title.contains(expected),
                "Expected title to contain '" + expected + "' but was: " + title);
    }

    @Then("the user should still be on the alerts page")
    public void theUserShouldStillBeOnAlertsPage() {
        String currentUrl = driverFactory.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("alerts"),
                "User should still be on alerts page but URL is: " + currentUrl);
    }
}
