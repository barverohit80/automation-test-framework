package com.automation.uitestgen.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client for calling the Anthropic Claude API to generate UI test code.
 * Supports both text-only and vision (screenshot) modes.
 */
@Slf4j
@Component
public class ClaudeUIClient {

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.model:claude-sonnet-4-20250514}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    /**
     * Generate test code from a text prompt (DOM + accessibility tree).
     */
    public String generate(String systemPrompt, String userPrompt) {
        validateApiKey();

        HttpHeaders headers = buildHeaders();

        Map<String, Object> body = Map.of(
            "model",      model,
            "max_tokens", 8192,
            "system",     systemPrompt,
            "messages",   List.of(Map.of("role", "user", "content", userPrompt))
        );

        log.info("[ClaudeUIClient] Calling Claude API (model={})...", model);

        ResponseEntity<Map> response = restTemplate.exchange(
            API_URL, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );

        return extractText(response);
    }

    /**
     * Vision mode — sends a screenshot alongside the text prompt.
     * Use when the page is canvas-heavy or DOM doesn't reflect visual layout.
     */
    public String generateWithScreenshot(String systemPrompt, String userPrompt, String base64Png) {
        validateApiKey();

        HttpHeaders headers = buildHeaders();

        Map<String, Object> imageBlock = Map.of(
            "type",   "image",
            "source", Map.of(
                "type",       "base64",
                "media_type", "image/png",
                "data",       base64Png
            )
        );
        Map<String, Object> textBlock = Map.of("type", "text", "text", userPrompt);

        Map<String, Object> body = Map.of(
            "model",      model,
            "max_tokens", 8192,
            "system",     systemPrompt,
            "messages",   List.of(Map.of(
                "role",    "user",
                "content", List.of(imageBlock, textBlock)
            ))
        );

        log.info("[ClaudeUIClient] Calling Claude API with screenshot (model={})...", model);

        ResponseEntity<Map> response = restTemplate.exchange(
            API_URL, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );

        return extractText(response);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String extractText(ResponseEntity<Map> response) {
        List<Map<String, Object>> content = (List<Map<String, Object>>)
            response.getBody().get("content");
        return (String) content.get(0).get("text");
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Anthropic API key not configured. Set 'anthropic.api.key' in application YAML "
                + "or ANTHROPIC_API_KEY environment variable.");
        }
    }
}
