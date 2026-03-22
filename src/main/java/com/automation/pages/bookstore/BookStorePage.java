package com.automation.pages.bookstore;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DemoQA Book Store Page — https://demoqa.com/books
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class BookStorePage extends BasePage {

    @FindBy(id = "searchBox")
    private WebElement searchBox;

    private static final By BOOK_LINKS = By.cssSelector(".action-buttons a");
    private static final By TABLE_ROWS = By.cssSelector(".rt-tbody .rt-tr-group");

    public void open() {
        navigateTo("/books");
    }

    public void searchFor(String text) {
        log.info("Searching for book: {}", text);
        type(searchBox, text);
    }

    public List<WebElement> getBookLinks() {
        return findElements(BOOK_LINKS);
    }

    public int getBookCount() {
        List<WebElement> links = getBookLinks();
        // Filter out empty rows
        return (int) links.stream().filter(l -> !l.getText().isBlank()).count();
    }

    public boolean isBookDisplayed(String title) {
        List<WebElement> links = getBookLinks();
        return links.stream().anyMatch(l -> l.getText().contains(title));
    }

    public void clickBook(String title) {
        log.info("Clicking book: {}", title);
        List<WebElement> links = getBookLinks();
        for (WebElement link : links) {
            if (link.getText().contains(title)) {
                scrollToElement(link);
                click(link);
                return;
            }
        }
        throw new IllegalArgumentException("Book not found: " + title);
    }
}
