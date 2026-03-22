@book-store
Feature: DemoQA Book Store
  As a user I want to browse and search books in the store

  @smoke
  Scenario: Book store displays books
    Given the user is on the book store page
    Then books should be displayed in the store

  @smoke
  Scenario: Search for a specific book
    Given the user is on the book store page
    When the user searches for book "JavaScript"
    Then the book "JavaScript" should be displayed in results

  @regression
  Scenario: Search for non-existent book returns no results
    Given the user is on the book store page
    When the user searches for book "NonExistentBook12345"
    Then the book "NonExistentBook12345" should not be displayed in results
