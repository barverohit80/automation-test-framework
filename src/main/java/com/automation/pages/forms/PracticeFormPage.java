package com.automation.pages.forms;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DemoQA Practice Form Page — https://demoqa.com/automation-practice-form
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class PracticeFormPage extends BasePage {

    @FindBy(id = "firstName")
    private WebElement firstNameInput;

    @FindBy(id = "lastName")
    private WebElement lastNameInput;

    @FindBy(id = "userEmail")
    private WebElement emailInput;

    @FindBy(id = "userNumber")
    private WebElement mobileInput;

    @FindBy(id = "subjectsInput")
    private WebElement subjectsInput;

    @FindBy(id = "currentAddress")
    private WebElement currentAddressInput;

    @FindBy(id = "submit")
    private WebElement submitButton;

    // Radio buttons
    private static final By GENDER_MALE = By.cssSelector("label[for='gender-radio-1']");
    private static final By GENDER_FEMALE = By.cssSelector("label[for='gender-radio-2']");
    private static final By GENDER_OTHER = By.cssSelector("label[for='gender-radio-3']");

    // Hobbies checkboxes
    private static final By HOBBY_SPORTS = By.cssSelector("label[for='hobbies-checkbox-1']");
    private static final By HOBBY_READING = By.cssSelector("label[for='hobbies-checkbox-2']");
    private static final By HOBBY_MUSIC = By.cssSelector("label[for='hobbies-checkbox-3']");

    // Confirmation modal
    private static final By MODAL_TITLE = By.id("example-modal-sizes-title-lg");
    private static final By MODAL_BODY = By.cssSelector(".modal-body");
    private static final By MODAL_CLOSE = By.id("closeLargeModal");

    public void open() {
        navigateTo("/automation-practice-form");
    }

    public void enterFirstName(String name) {
        log.info("Entering first name: {}", name);
        type(firstNameInput, name);
    }

    public void enterLastName(String name) {
        log.info("Entering last name: {}", name);
        type(lastNameInput, name);
    }

    public void enterEmail(String email) {
        log.info("Entering email: {}", email);
        type(emailInput, email);
    }

    public void selectGender(String gender) {
        log.info("Selecting gender: {}", gender);
        switch (gender.toLowerCase()) {
            case "male" -> click(GENDER_MALE);
            case "female" -> click(GENDER_FEMALE);
            case "other" -> click(GENDER_OTHER);
        }
    }

    public void enterMobileNumber(String mobile) {
        log.info("Entering mobile: {}", mobile);
        type(mobileInput, mobile);
    }

    public void enterSubject(String subject) {
        log.info("Entering subject: {}", subject);
        subjectsInput.sendKeys(subject);
        subjectsInput.sendKeys(Keys.ENTER);
    }

    public void selectHobby(String hobby) {
        log.info("Selecting hobby: {}", hobby);
        switch (hobby.toLowerCase()) {
            case "sports" -> click(HOBBY_SPORTS);
            case "reading" -> click(HOBBY_READING);
            case "music" -> click(HOBBY_MUSIC);
        }
    }

    public void enterCurrentAddress(String address) {
        log.info("Entering address: {}", address);
        type(currentAddressInput, address);
    }

    public void clickSubmit() {
        log.info("Submitting practice form");
        scrollToElement(submitButton);
        jsClick(submitButton);
    }

    public boolean isConfirmationModalDisplayed() {
        return isDisplayed(MODAL_TITLE);
    }

    public String getConfirmationModalTitle() {
        return getText(MODAL_TITLE);
    }

    public String getConfirmationModalBody() {
        return getText(MODAL_BODY);
    }

    public void closeConfirmationModal() {
        click(MODAL_CLOSE);
    }
}
