package com.automation.steps;

import com.automation.pages.elements.ButtonsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class ButtonsSteps {

    @Autowired private ButtonsPage buttonsPage;

    @Given("the user is on the buttons page")
    public void theUserIsOnTheButtonsPage() {
        buttonsPage.open();
    }

    @When("the user double clicks the button")
    public void theUserDoubleClicks() {
        buttonsPage.performDoubleClick();
    }

    @When("the user right clicks the button")
    public void theUserRightClicks() {
        buttonsPage.performRightClick();
    }

    @When("the user clicks the dynamic click button")
    public void theUserDynamicClicks() {
        buttonsPage.performDynamicClick();
    }

    @Then("the double click message should be displayed")
    public void doubleClickMessageDisplayed() {
        String msg = buttonsPage.getDoubleClickMessage();
        assertEquals("You have done a double click", msg);
    }

    @Then("the right click message should be displayed")
    public void rightClickMessageDisplayed() {
        String msg = buttonsPage.getRightClickMessage();
        assertEquals("You have done a right click", msg);
    }

    @Then("the dynamic click message should be displayed")
    public void dynamicClickMessageDisplayed() {
        String msg = buttonsPage.getDynamicClickMessage();
        assertEquals("You have done a dynamic click", msg);
    }
}
