@widgets @progress-bar
Feature: DemoQA Progress Bar
  As a user I want to test the progress bar start, stop and reset

  @smoke
  Scenario: Start progress bar and verify it moves
    Given the user is on the progress bar page
    When the user starts the progress bar
    And the user waits for the progress bar to reach 25 percent
    And the user stops the progress bar
    Then the progress bar value should be greater than 20

  @regression
  Scenario: Progress bar reaches 100 and shows reset button
    Given the user is on the progress bar page
    When the user starts the progress bar
    And the user waits for the progress bar to reach 100 percent
    Then the reset button should be displayed
