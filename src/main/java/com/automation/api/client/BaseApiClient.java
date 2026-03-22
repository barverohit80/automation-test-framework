package com.automation.api.client;

import com.automation.config.EnvironmentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Base API client providing common HTTP operations via RestTemplate.
 * Subclass this for each API domain (BookStore, Account, etc.).
 *
 * Features:
 *   - Automatic base URL resolution from EnvironmentConfig
 *   - Bearer token support via authorization header
 *   - Full response capture (status + headers + body) even on 4xx/5xx
 *   - JSON content type by default
 */
@Slf4j
public abstract class BaseApiClient {

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    protected EnvironmentConfig config;

    protected String getBaseUrl() {
        return config.getApiBaseUrl();
    }

    // ── GET ──────────────────────────────────────────────────────────

    public <T> ResponseEntity<T> get(String path, Class<T> responseType) {
        return get(path, responseType, null);
    }

    public <T> ResponseEntity<T> get(String path, Class<T> responseType, String bearerToken) {
        String url = getBaseUrl() + path;
        HttpHeaders headers = buildHeaders(bearerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return exchange(url, HttpMethod.GET, entity, responseType);
    }

    // ── GET with query params ────────────────────────────────────────

    public <T> ResponseEntity<T> getWithParams(String path, Map<String, String> params,
                                                Class<T> responseType) {
        StringBuilder url = new StringBuilder(getBaseUrl() + path);
        if (params != null && !params.isEmpty()) {
            url.append("?");
            params.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
            url.setLength(url.length() - 1); // remove trailing &
        }
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(null));
        return exchange(url.toString(), HttpMethod.GET, entity, responseType);
    }

    // ── POST ─────────────────────────────────────────────────────────

    public <T> ResponseEntity<T> post(String path, Object requestBody, Class<T> responseType) {
        return post(path, requestBody, responseType, null);
    }

    public <T> ResponseEntity<T> post(String path, Object requestBody,
                                       Class<T> responseType, String bearerToken) {
        String url = getBaseUrl() + path;
        HttpHeaders headers = buildHeaders(bearerToken);
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);

        return exchange(url, HttpMethod.POST, entity, responseType);
    }

    // ── DELETE ────────────────────────────────────────────────────────

    public <T> ResponseEntity<T> delete(String path, Class<T> responseType, String bearerToken) {
        String url = getBaseUrl() + path;
        HttpHeaders headers = buildHeaders(bearerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return exchange(url, HttpMethod.DELETE, entity, responseType);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private HttpHeaders buildHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.setBearerAuth(bearerToken);
        }
        return headers;
    }

    /**
     * Execute HTTP request and capture the FULL response even on error status codes.
     * This allows step definitions to assert on 4xx/5xx responses without exceptions.
     */
    private <T> ResponseEntity<T> exchange(String url, HttpMethod method,
                                            HttpEntity<?> entity, Class<T> responseType) {
        try {
            return restTemplate.exchange(url, method, entity, responseType);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.warn("API error response: {} {} → {}", method, url, ex.getStatusCode());
            @SuppressWarnings("unchecked")
            ResponseEntity<T> errorResponse = (ResponseEntity<T>) ResponseEntity
                    .status(ex.getStatusCode())
                    .headers(ex.getResponseHeaders())
                    .body(ex.getResponseBodyAs(responseType));
            return errorResponse;
        }
    }
}
