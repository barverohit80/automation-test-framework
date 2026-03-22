package com.automation.pages.home;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DemoQA Home Page — https://demoqa.com/
 * Shows 6 category cards: Elements, Forms, Alerts, Widgets, Interactions, Book Store
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class HomePage extends BasePage {

    private static final By CATEGORY_CARDS = By.cssSelector(".category-cards .card");
    private static final By BANNER_IMAGE = By.cssSelector(".home-banner a img");

    public void open() {
        navigateTo("");
    }

    public boolean isHomePageDisplayed() {
        return isDisplayed(BANNER_IMAGE);
    }

    public List<WebElement> getCategoryCards() {
        return findElements(CATEGORY_CARDS);
    }

    public int getCategoryCardCount() {
        return getCategoryCards().size();
    }

    public void clickCategoryCard(String cardName) {
        log.info("Clicking category card: {}", cardName);
        List<WebElement> cards = getCategoryCards();
        for (WebElement card : cards) {
            if (card.getText().trim().contains(cardName)) {
                scrollToElement(card);
                click(card);
                return;
            }
        }
        throw new IllegalArgumentException("Category card not found: " + cardName);
    }
}
