package com.automation.pages.elements;

import com.automation.pages.base.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * DemoQA Web Tables Page — https://demoqa.com/webtables
 */
@Slf4j
@Component
@Scope("cucumber-glue")
public class WebTablesPage extends BasePage {

    @FindBy(id = "addNewRecordButton")
    private WebElement addButton;

    @FindBy(id = "searchBox")
    private WebElement searchBox;

    // Registration form (modal)
    @FindBy(id = "firstName")
    private WebElement firstNameInput;

    @FindBy(id = "lastName")
    private WebElement lastNameInput;

    @FindBy(id = "userEmail")
    private WebElement emailInput;

    @FindBy(id = "age")
    private WebElement ageInput;

    @FindBy(id = "salary")
    private WebElement salaryInput;

    @FindBy(id = "department")
    private WebElement departmentInput;

    @FindBy(id = "submit")
    private WebElement submitButton;

    private static final By TABLE_ROWS = By.cssSelector(".rt-tbody .rt-tr-group");
    private static final By EDIT_BUTTON = By.cssSelector("[title='Edit']");
    private static final By DELETE_BUTTON = By.cssSelector("[title='Delete']");

    public void open() {
        navigateTo("/webtables");
    }

    public void clickAdd() {
        log.info("Clicking Add button");
        click(addButton);
    }

    public void fillRegistrationForm(String firstName, String lastName, String email,
                                     String age, String salary, String department) {
        log.info("Filling registration form for: {} {}", firstName, lastName);
        type(firstNameInput, firstName);
        type(lastNameInput, lastName);
        type(emailInput, email);
        type(ageInput, age);
        type(salaryInput, salary);
        type(departmentInput, department);
    }

    public void clickSubmit() {
        click(submitButton);
        // Wait for the registration modal to close
        new WebDriverWait(getDriver(), Duration.ofSeconds(5))
                .until(ExpectedConditions.invisibilityOfElementLocated(By.className("modal-content")));
    }

    public void searchFor(String text) {
        log.info("Searching for: {}", text);
        type(searchBox, text);
    }

    public List<WebElement> getTableRows() {
        return findElements(TABLE_ROWS);
    }

    public boolean isRecordPresent(String text) {
        List<WebElement> rows = getTableRows();
        for (WebElement row : rows) {
            if (row.getText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    public void editRecord(String name) {
        searchFor(name);
        List<WebElement> rows = getTableRows();
        for (WebElement row : rows) {
            if (row.getText().contains(name)) {
                row.findElement(EDIT_BUTTON).click();
                return;
            }
        }
    }

    public void deleteRecord(String name) {
        searchFor(name);
        List<WebElement> rows = getTableRows();
        for (WebElement row : rows) {
            if (row.getText().contains(name)) {
                row.findElement(DELETE_BUTTON).click();
                return;
            }
        }
    }
}
