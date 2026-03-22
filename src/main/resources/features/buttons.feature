@elements @buttons
Feature: DemoQA Buttons
  As a user I want to test double click, right click and dynamic click

  @smoke
  Scenario: Double click button shows message
    Given the user is on the buttons page
    When the user double clicks the button
    Then the double click message should be displayed

  @smoke
  Scenario: Right click button shows message
    Given the user is on the buttons page
    When the user right clicks the button
    Then the right click message should be displayed

  @smoke
  Scenario: Dynamic click button shows message
    Given the user is on the buttons page
    When the user clicks the dynamic click button
    Then the dynamic click message should be displayed
