package com.quiz.ai;

/**
 * Abstraction over LLM providers. Implementations can be swapped via configuration.
 */
public interface LlmProvider {

    /**
     * Send a prompt and get a text response.
     *
     * @param systemPrompt  system-level instructions
     * @param userPrompt    the user/task prompt
     * @return raw text response from the model
     */
    String generateText(String systemPrompt, String userPrompt);

    /**
     * Send a prompt and get a structured JSON response.
     * The model is instructed to output valid JSON matching the expected schema.
     *
     * @param systemPrompt  system-level instructions
     * @param userPrompt    the user/task prompt including JSON schema hints
     * @return JSON string response
     */
    String generateJson(String systemPrompt, String userPrompt);
}
