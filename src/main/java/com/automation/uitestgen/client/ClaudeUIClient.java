package com.automation.uitestgen.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Claude (Anthropic) implementation of LlmClient.
 * Active when: testgen.llm.provider=claude (default if property is missing).
 *
 * application.yml:
 *   testgen:
 *     llm:
 *       provider: claude
 *   anthropic:
 *     api:
 *       key: ${ANTHROPIC_API_KEY}
 *       model: claude-sonnet-4-6          # or claude-opus-4-6 / claude-haiku-4-5-20251001
 */
@Slf4j
@Component("claudeClient")
@ConditionalOnProperty(name = "testgen.llm.provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeUIClient implements LlmClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.model:claude-sonnet-4-6}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
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

    @Override
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

    @Override
    public String providerName() {
        return "Claude (" + model + ")";
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
