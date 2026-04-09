@debug
Feature: Debug Locator Extraction

  Scenario: Debug extraction on PracticeFormPage
    Given the user is on the practice form page
    Then debug extract locators for page "PracticeFormPage"
    Then ultra-simple extract all elements
    Then print DOM diagnostic report

  Scenario: Debug extraction on HomePage
    Given the user is on the home page
    Then debug extract locators for page "HomePage"
    Then ultra-simple extract all elements
    Then print DOM diagnostic report

  Scenario: Generate locators after debugging
    Given the user is on the practice form page
    Then print page load status
    Then ultra-simple extract all elements
    Then generate locators for page "PracticeFormPage"
