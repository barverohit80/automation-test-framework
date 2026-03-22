package com.automation.steps;

import com.automation.context.TestContext;
import com.automation.pages.bookstore.BookStorePage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class BookStoreSteps {

    @Autowired private BookStorePage bookStorePage;
    @Autowired private TestContext testContext;

    @Given("the user is on the book store page")
    public void theUserIsOnTheBookStorePage() {
        bookStorePage.open();
    }

    @When("the user searches for book {string}")
    public void theUserSearchesForBook(String title) {
        testContext.set("searchedBook", title);
        bookStorePage.searchFor(title);
    }

    @Then("the book {string} should be displayed in results")
    public void theBookShouldBeDisplayed(String title) {
        assertTrue(bookStorePage.isBookDisplayed(title),
                "Book '" + title + "' should be displayed in search results");
    }

    @Then("the book {string} should not be displayed in results")
    public void theBookShouldNotBeDisplayed(String title) {
        assertFalse(bookStorePage.isBookDisplayed(title),
                "Book '" + title + "' should NOT be displayed in search results");
    }

    @When("the user clicks on book {string}")
    public void theUserClicksOnBook(String title) {
        bookStorePage.clickBook(title);
    }

    @Then("books should be displayed in the store")
    public void booksShouldBeDisplayed() {
        int count = bookStorePage.getBookCount();
        assertTrue(count > 0, "At least one book should be displayed");
    }
}
