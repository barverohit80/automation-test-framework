@search @book-store
Feature: DemoQA Book Store Search
  Search for books using specific and random terms

  @smoke
  Scenario: Search with specific term
    Given the user is on the book store page
    When the user searches for book "Git"
    Then the book "Git" should be displayed in results

  @random-data
  Scenario: Search with random term returns no results
    Given the user is on the book store page
    When the user searches for book "randomNonExistent99999"
    Then the book "randomNonExistent99999" should not be displayed in results
