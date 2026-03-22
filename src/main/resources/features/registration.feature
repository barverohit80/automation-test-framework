#@registration
#Feature: DemoQA Registration via Book Store
#  Demonstrates runtime random value generation via custom parameter types
#  against the DemoQA login/register page.
#
#  @random-data @smoke
#  Scenario: Attempt login with random credentials
#    Given the user is on the login page
#    When the user enters a random username randomAlphanumeric
#    And the user enters password "RandomPass123!"
#    And the user clicks the login button
#    Then the login error message should be displayed
#
#  @random-data
#  Scenario: Attempt login with another random username
#    Given the user is on the login page
#    When the user enters a random username randomAlphanumeric
#    And the user enters password "AnotherRandom456!"
#    And the user clicks the login button
#    Then the login error message should be displayed
#
#  @newbrowser @random-data
#  Scenario: Fresh browser login attempt with random data
#    Given the user is on the login page
#    When the user enters a random username randomAlphanumeric
#    And the user enters password "FreshSession789!"
#    And the user clicks the login button
#    Then the login error message should be displayed
