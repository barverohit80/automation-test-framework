@elements @text-box
Feature: DemoQA Text Box
  As a user I want to fill and submit the text box form

  @smoke
  Scenario: Submit text box form with valid data
    Given the user is on the text box page
    When the user enters full name "John Doe"
    And the user enters email "john.doe@example.com"
    And the user enters current address "123 Main Street, New York"
    And the user enters permanent address "456 Oak Avenue, Los Angeles"
    And the user submits the text box form
    Then the submitted data should be displayed in the output
    And the output name should contain "John Doe"
    And the output email should contain "john.doe@example.com"

  @random-data
  Scenario: Submit text box form with random data
    Given the user is on the text box page
    When the user enters random full name randomAlphanumeric
    And the user enters random email randomEmail
    And the user enters current address "Random Street 42"
    And the user submits the text box form
    Then the submitted data should be displayed in the output
