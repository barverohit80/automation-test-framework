package com.automation.uitestgen.client;

/**
 * Abstraction for LLM providers used by the UI test generation pipeline.
 * Implementations are activated conditionally via testgen.llm.provider property.
 */
public interface LlmClient {

    /** Generate test code from a text prompt (DOM + accessibility tree). */
    String generate(String systemPrompt, String userPrompt);

    /** Vision mode — sends a screenshot alongside the text prompt. */
    String generateWithScreenshot(String systemPrompt, String userPrompt, String base64Png);

    /** Human-readable provider name for logging. */
    String providerName();
}
