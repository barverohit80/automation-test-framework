@forms @practice-form
Feature: DemoQA Practice Form
  As a user I want to fill and submit the student registration form

  @smoke @generate
  Scenario: Submit practice form with required fields
    Given the user is on the practice form page
    Then generate locators for page "PracticeFormPage"
    When the user fills in the practice form with first name "Rohit" last name "Barve" email "rohit@test.com" gender "Male" mobile "1234567890"
    And the user selects hobby "Sports"
    And the user enters form address "Mumbai, India"
    And the user submits the practice form
    Then the confirmation modal should be displayed
    And the confirmation modal title should be "Thanks for submitting the form"
    And the confirmation modal should contain "Rohit Barve"

  @regression
  Scenario: Submit practice form with female gender and reading hobby
    Given the user is on the practice form page
    When the user fills in the practice form with first name "Jane" last name "Doe" email "jane@test.com" gender "Female" mobile "9876543210"
    And the user selects hobby "Reading"
    And the user enters subject "Maths"
    And the user submits the practice form
    Then the confirmation modal should be displayed
    And the confirmation modal should contain "Jane Doe"
