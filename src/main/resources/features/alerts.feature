@alerts
Feature: DemoQA Alerts
  As a user I want to handle JavaScript alerts, confirms and prompts

  @smoke
  Scenario: Accept a simple alert
    Given the user is on the alerts page
    When the user clicks the alert button
    And the user accepts the alert
    Then the user should still be on the alerts page

  @smoke
  Scenario: Accept a confirm dialog
    Given the user is on the alerts page
    When the user clicks the confirm button
    And the user accepts the alert
    Then the confirm result should contain "Ok"

  @smoke
  Scenario: Dismiss a confirm dialog
    Given the user is on the alerts page
    When the user clicks the confirm button
    And the user dismisses the alert
    Then the confirm result should contain "Cancel"

  @regression
  Scenario: Enter text in a prompt dialog
    Given the user is on the alerts page
    When the user clicks the prompt button
    And the user enters "Automation Test" in the prompt and accepts
    Then the prompt result should contain "Automation Test"

  @regression
  Scenario: Timer alert appears after 5 seconds
    Given the user is on the alerts page
    When the user clicks the timer alert button
    And the user accepts the alert
    Then the user should still be on the alerts page
