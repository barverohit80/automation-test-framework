@home
Feature: DemoQA Home Page
  As a user I want to verify the home page displays all category cards

  @smoke
  Scenario: Home page displays all 6 category cards
    Given the user is on the home page
    Then the home page should be displayed
    And the home page should display 6 category cards

  @smoke
  Scenario: Navigate to Elements section from home page
    Given the user is on the home page
    When the user clicks on "Elements" category card
    Then the page URL should contain "elements"

  @smoke
  Scenario: Navigate to Forms section from home page
    Given the user is on the home page
    When the user clicks on "Forms" category card
    Then the page URL should contain "forms"
