#@login
#Feature: DemoQA Login Page
#  As a user I want to interact with the login page on demoqa.com
#
#  @smoke
#  Scenario: Login page is accessible
#    Given the user is on the login page
#    Then the login page should be displayed
#
#  @smoke @negative
#  Scenario: Login with invalid credentials shows error
#    Given the user is on the login page
#    When the user logs in with username "invalid_user" and password "wrong_pass"
#    Then the login error message should be displayed
#    And the login error message should contain "Invalid"
#
#  @random-data @negative
#  Scenario: Login with random username shows error
#    Given the user is on the login page
#    When the user enters a random username randomAlphanumeric
#    And the user enters password "test123"
#    And the user clicks the login button
#    Then the login error message should be displayed
#
#  @newbrowser @security
#  Scenario: Fresh session login attempt
#    Given the user is on the login page
#    When the user logs in with username "fresh_user" and password "fresh_pass"
#    Then the login error message should be displayed
