@diagnostic @skip
Feature: Diagnostic Features for Debugging Locator Extraction

  Scenario: Diagnose HomePage structure
    Given the user is on the home page
    Then print page load status
    Then print DOM diagnostic report
    Then print all elements matching interactable tags
    Then print all form inputs on page

  Scenario: Diagnose PracticeFormPage structure
    Given the user is on the practice form page
    Then print page load status
    Then print DOM diagnostic report
    Then print all elements matching interactable tags
    Then print all form inputs on page

  Scenario: Diagnose LoginPage structure
    Given the user is on the login page
    Then print page load status
    Then print DOM diagnostic report
    Then print all elements matching interactable tags
    Then print all form inputs on page
