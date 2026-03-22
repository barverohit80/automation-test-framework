@elements @web-tables
Feature: DemoQA Web Tables
  As a user I want to add, search and delete records in the web table

  @smoke
  Scenario: Add a new record to the table
    Given the user is on the web tables page
    When the user adds a new record with first name "Rohit" last name "Barve" email "rohit@test.com" age "30" salary "80000" department "Engineering"
    Then the record with "Rohit" should be present in the table

  @smoke
  Scenario: Search for an existing record
    Given the user is on the web tables page
    When the user searches for "Cierra" in the table
    Then the record with "Cierra" should be present in the table

  @regression
  Scenario: Delete a record from the table
    Given the user is on the web tables page
    When the user adds a new record with first name "ToDelete" last name "User" email "del@test.com" age "25" salary "50000" department "QA"
    And the record with "ToDelete" should be present in the table
    When the user deletes the record with "ToDelete"
    Then the record with "ToDelete" should not be present in the table

  @regression
  Scenario: Search returns no results for non-existent record
    Given the user is on the web tables page
    When the user searches for "NonExistentName12345" in the table
    Then the record with "NonExistentName12345" should not be present in the table
