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
 * Google Gemini implementation of LlmClient.
 * Active when: testgen.llm.provider=gemini
 *
 * application.yml:
 *   testgen:
 *     llm:
 *       provider: gemini
 *   gemini:
 *     api:
 *       key: ${GEMINI_API_KEY}
 *       model: gemini-2.5-flash           # free tier — or gemini-2.5-pro
 *
 * Free tier limits (no credit card needed):
 *   gemini-2.5-flash      → 10 RPM, 250 req/day
 *   gemini-2.5-flash-lite → 15 RPM, 1000 req/day
 *   gemini-2.5-pro        → 5 RPM,  100 req/day
 *
 * Get your key: aistudio.google.com → Get API key
 */
@Slf4j
@Component("geminiClient")
@ConditionalOnProperty(name = "testgen.llm.provider", havingValue = "gemini")
public class GeminiClient implements LlmClient {

    private static final String BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/";

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        validateApiKey();

        String url = BASE_URL + model + ":generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
            "system_instruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
            ),
            "contents", List.of(Map.of(
                "role",  "user",
                "parts", List.of(Map.of("text", userPrompt))
            )),
            "generationConfig", Map.of(
                "maxOutputTokens", 8192,
                "temperature",     0.2
            )
        );

        log.info("[GeminiClient] Calling Gemini API (model={})...", model);

        return call(url, body);
    }

    @Override
    public String generateWithScreenshot(String systemPrompt, String userPrompt, String base64Png) {
        validateApiKey();

        String url = BASE_URL + model + ":generateContent?key=" + apiKey;

        Map<String, Object> imagePart = Map.of(
            "inline_data", Map.of(
                "mime_type", "image/png",
                "data",      base64Png
            )
        );
        Map<String, Object> textPart = Map.of("text", userPrompt);

        Map<String, Object> body = Map.of(
            "system_instruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
            ),
            "contents", List.of(Map.of(
                "role",  "user",
                "parts", List.of(imagePart, textPart)
            )),
            "generationConfig", Map.of(
                "maxOutputTokens", 8192,
                "temperature",     0.2
            )
        );

        log.info("[GeminiClient] Calling Gemini API with screenshot (model={})...", model);

        return call(url, body);
    }

    @Override
    public String providerName() {
        return "Gemini (" + model + ")";
    }

    @SuppressWarnings("unchecked")
    private String call(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );

        List<Map<String, Object>> candidates =
            (List<Map<String, Object>>) response.getBody().get("candidates");

        Map<String, Object> content =
            (Map<String, Object>) candidates.get(0).get("content");

        List<Map<String, Object>> parts =
            (List<Map<String, Object>>) content.get("parts");

        return (String) parts.get(0).get("text");
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Gemini API key not configured. Set 'gemini.api.key' in application YAML "
                + "or GEMINI_API_KEY environment variable.");
        }
    }
}
