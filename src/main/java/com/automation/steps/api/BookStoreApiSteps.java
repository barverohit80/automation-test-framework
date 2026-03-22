package com.automation.steps.api;

import com.automation.api.client.BookStoreApiClient;
import com.automation.api.model.Book;
import com.automation.api.model.BooksResponse;
import com.automation.api.model.TokenResponse;
import com.automation.context.TestContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
public class BookStoreApiSteps {

    @Autowired private BookStoreApiClient bookStoreApiClient;
    @Autowired private TestContext testContext;

    // ── GET All Books ────────────────────────────────────────────────

    @When("the user sends a GET request to fetch all books")
    public void fetchAllBooks() {
        ResponseEntity<BooksResponse> response = bookStoreApiClient.getAllBooks();
        testContext.set("apiResponse", response);
        testContext.set("apiStatusCode", response.getStatusCode().value());
        testContext.set("apiHeaders", response.getHeaders());

        if (response.getBody() != null) {
            testContext.set("booksList", response.getBody().getBooks());
        }
        log.info("GET all books → status: {}, books: {}",
                response.getStatusCode().value(),
                response.getBody() != null ? response.getBody().getBooks().size() : 0);
    }

    // ── GET Book by ISBN ─────────────────────────────────────────────

    @When("the user sends a GET request for book with ISBN {string}")
    public void fetchBookByIsbn(String isbn) {
        ResponseEntity<Book> response = bookStoreApiClient.getBookByIsbn(isbn);
        testContext.set("apiResponse", response);
        testContext.set("apiStatusCode", response.getStatusCode().value());
        testContext.set("apiHeaders", response.getHeaders());

        if (response.getBody() != null) {
            testContext.set("book", response.getBody());
        }
        log.info("GET book ISBN={} → status: {}", isbn, response.getStatusCode().value());
    }

    // ── POST Generate Token ──────────────────────────────────────────

    @When("the user sends a POST request to generate token with username {string} and password {string}")
    public void generateToken(String userName, String password) {
        ResponseEntity<TokenResponse> response = bookStoreApiClient.generateToken(userName, password);
        testContext.set("apiResponse", response);
        testContext.set("apiStatusCode", response.getStatusCode().value());

        if (response.getBody() != null) {
            testContext.set("tokenResponse", response.getBody());
        }
        log.info("POST generateToken for '{}' → status: {}", userName, response.getStatusCode().value());
    }

    // ── POST Check Authorization ─────────────────────────────────────

    @When("the user sends a POST request to check authorization with username {string} and password {string}")
    public void checkAuthorization(String userName, String password) {
        ResponseEntity<String> response = bookStoreApiClient.checkAuthorized(userName, password);
        testContext.set("apiResponse", response);
        testContext.set("apiStatusCode", response.getStatusCode().value());
        log.info("POST authorized for '{}' → status: {}", userName, response.getStatusCode().value());
    }

    // ── Status Code Assertions ───────────────────────────────────────

    @Then("the API response status code should be {int}")
    public void verifyStatusCode(int expectedStatus) {
        int actual = testContext.getInt("apiStatusCode");
        assertEquals(expectedStatus, actual,
                "Expected status " + expectedStatus + " but got " + actual);
    }

    // ── Books List Assertions ────────────────────────────────────────

    @And("the response should contain a list of books")
    public void responseContainsBooks() {
        List<Book> books = testContext.get("booksList", List.class);
        assertNotNull(books, "Books list should not be null");
        assertFalse(books.isEmpty(), "Books list should not be empty");
        log.info("Books list contains {} books", books.size());
    }

    @And("each book should have a title and ISBN")
    public void eachBookHasTitleAndIsbn() {
        List<Book> books = testContext.get("booksList", List.class);
        for (Book book : books) {
            assertNotNull(book.getTitle(), "Book title should not be null");
            assertFalse(book.getTitle().isBlank(), "Book title should not be blank");
            assertNotNull(book.getIsbn(), "Book ISBN should not be null");
            assertFalse(book.getIsbn().isBlank(), "Book ISBN should not be blank");
        }
    }

    // ── Single Book Assertions ───────────────────────────────────────

    @And("the response book title should be {string}")
    public void verifyBookTitle(String expectedTitle) {
        Book book = testContext.get("book", Book.class);
        assertNotNull(book, "Book response should not be null");
        assertEquals(expectedTitle, book.getTitle());
    }

    @And("the response book author should be {string}")
    public void verifyBookAuthor(String expectedAuthor) {
        Book book = testContext.get("book", Book.class);
        assertNotNull(book, "Book response should not be null");
        assertEquals(expectedAuthor, book.getAuthor());
    }

    @And("the response book should have pages greater than {int}")
    public void verifyBookPages(int minPages) {
        Book book = testContext.get("book", Book.class);
        assertNotNull(book, "Book response should not be null");
        assertTrue(book.getPages() > minPages,
                "Book should have more than " + minPages + " pages, got " + book.getPages());
    }

    // ── Token Response Assertions ────────────────────────────────────

    @And("the token response status should be {string}")
    public void verifyTokenStatus(String expectedStatus) {
        TokenResponse token = testContext.get("tokenResponse", TokenResponse.class);
        assertNotNull(token, "Token response should not be null");
        assertEquals(expectedStatus, token.getStatus());
    }

    @And("the token response result should be {string}")
    public void verifyTokenResult(String expectedResult) {
        TokenResponse token = testContext.get("tokenResponse", TokenResponse.class);
        assertNotNull(token, "Token response should not be null");
        assertEquals(expectedResult, token.getResult());
    }

    // ── Response Header Assertions ───────────────────────────────────

    @And("the response content type should contain {string}")
    public void verifyContentType(String expectedContentType) {
        org.springframework.http.HttpHeaders headers = testContext.get("apiHeaders",
                org.springframework.http.HttpHeaders.class);
        assertNotNull(headers, "Response headers should not be null");
        String contentType = headers.getFirst(org.springframework.http.HttpHeaders.CONTENT_TYPE);
        assertNotNull(contentType, "Content-Type header should be present");
        assertTrue(contentType.contains(expectedContentType),
                "Content-Type '" + contentType + "' should contain '" + expectedContentType + "'");
    }
}
