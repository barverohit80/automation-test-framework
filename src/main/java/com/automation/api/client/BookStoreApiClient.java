package com.automation.api.client;

import com.automation.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * REST client for the DemoQA BookStore & Account APIs.
 *
 * Endpoints:
 *   GET  /BookStore/v1/Books            → List all books
 *   GET  /BookStore/v1/Book?ISBN=xxx    → Get book by ISBN
 *   POST /Account/v1/User              → Create a new user
 *   POST /Account/v1/GenerateToken     → Generate auth token
 *   POST /Account/v1/Authorized        → Check if user is authorized
 */
@Slf4j
@Component
public class BookStoreApiClient extends BaseApiClient {

    private static final String BOOKS_PATH = "/BookStore/v1/Books";
    private static final String BOOK_PATH = "/BookStore/v1/Book";
    private static final String CREATE_USER_PATH = "/Account/v1/User";
    private static final String GENERATE_TOKEN_PATH = "/Account/v1/GenerateToken";
    private static final String AUTHORIZED_PATH = "/Account/v1/Authorized";

    // ── BookStore ────────────────────────────────────────────────────

    public ResponseEntity<BooksResponse> getAllBooks() {
        log.info("Fetching all books");
        return get(BOOKS_PATH, BooksResponse.class);
    }

    public ResponseEntity<Book> getBookByIsbn(String isbn) {
        log.info("Fetching book with ISBN: {}", isbn);
        return getWithParams(BOOK_PATH, Map.of("ISBN", isbn), Book.class);
    }

    // ── Account ──────────────────────────────────────────────────────

    public ResponseEntity<String> createUser(String userName, String password) {
        log.info("Creating user: {}", userName);
        UserRequest request = new UserRequest(userName, password);
        return post(CREATE_USER_PATH, request, String.class);
    }

    public ResponseEntity<TokenResponse> generateToken(String userName, String password) {
        log.info("Generating token for user: {}", userName);
        UserRequest request = new UserRequest(userName, password);
        return post(GENERATE_TOKEN_PATH, request, TokenResponse.class);
    }

    public ResponseEntity<String> checkAuthorized(String userName, String password) {
        log.info("Checking authorization for user: {}", userName);
        UserRequest request = new UserRequest(userName, password);
        return post(AUTHORIZED_PATH, request, String.class);
    }
}
