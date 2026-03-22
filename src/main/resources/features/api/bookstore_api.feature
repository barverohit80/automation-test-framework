@api @bookstore-api
Feature: DemoQA BookStore REST API
  As a QA engineer I want to validate the BookStore API
  using REST calls to ensure backend services are working correctly

  # ── GET Endpoints ──────────────────────────────────────────────

  @smoke
  Scenario: Get all books from the store
    When the user sends a GET request to fetch all books
    Then the API response status code should be 200
    And the response should contain a list of books
    And each book should have a title and ISBN

  @smoke
  Scenario: Get a specific book by ISBN
    When the user sends a GET request for book with ISBN "9781449325862"
    Then the API response status code should be 200
    And the response book title should be "Git Pocket Guide"
    And the response book author should be "Richard E. Silverman"
    And the response book should have pages greater than 0

  @regression
  Scenario: Get book with invalid ISBN returns error
    When the user sends a GET request for book with ISBN "0000000000000"
    Then the API response status code should be 400

  # ── POST Endpoints ─────────────────────────────────────────────

  @regression
  Scenario: Generate token with invalid credentials
    When the user sends a POST request to generate token with username "invalidUser" and password "InvalidPass!1"
    Then the API response status code should be 200
    And the token response status should be "Failed"
    And the token response result should be "User authorization failed."

  @regression
  Scenario: Check authorization with invalid credentials
    When the user sends a POST request to check authorization with username "invalidUser" and password "InvalidPass!1"
    Then the API response status code should be 404

  # ── Response Validation ────────────────────────────────────────

  @smoke
  Scenario: Verify response headers for books API
    When the user sends a GET request to fetch all books
    Then the API response status code should be 200
    And the response content type should contain "application/json"

  @regression
  Scenario Outline: Get book details by ISBN
    When the user sends a GET request for book with ISBN "<isbn>"
    Then the API response status code should be 200
    And the response book title should be "<title>"

    Examples:
      | isbn          | title                                                     |
      | 9781449325862 | Git Pocket Guide                                          |
      | 9781449331818 | Learning JavaScript Design Patterns                       |
      | 9781449337711 | Designing Evolvable Web APIs with ASP.NET                 |
      | 9781491950296 | Programming JavaScript Applications                      |
