package com.quiz.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq implementation of LlmProvider (OpenAI compatible).
 * Uses the Groq REST API (api.groq.com).
 */
@Slf4j
public class GroqLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String model;
    private final int maxRetries;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    public GroqLlmProvider(String apiKey, String model, int maxRetries) {
        this.apiKey = apiKey;
        // Default to a robust Groq model if none provided
        this.model = (model != null && !model.isBlank() && !model.contains("gemini")) ? model : "openai/gpt-oss-120b";
        this.maxRetries = maxRetries;
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        return callGroq(systemPrompt, userPrompt, false);
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        return callGroq(systemPrompt, userPrompt, true);
    }

    private String callGroq(String systemPrompt, String userPrompt, boolean requireJson) {
        Exception lastException = null;
        int attempt = 0;
        long delay = 800;

        while (attempt < maxRetries) {
            attempt++;
            try {
                Map<String, Object> requestBody = buildRequest(systemPrompt, userPrompt, requireJson);

                String responseStr = restClient.post()
                        .uri(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode choices = root.path("choices");
                
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode message = choices.get(0).path("message");
                    if (!message.isMissingNode() && message.has("content")) {
                        return message.path("content").asText("");
                    }
                }

                throw new RuntimeException("Unexpected Groq response format: " + responseStr);

            } catch (Exception e) {
                lastException = e;
                log.warn("Groq call failed (attempt {}): {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                    delay *= 2;
                }
            }
        }

        throw new RuntimeException("Groq AI model unavailable after retries: " + 
            (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }

    private Map<String, Object> buildRequest(String systemPrompt, String userPrompt, boolean requireJson) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        
        // When using JSON mode on Groq, the prompt MUST explicitly mention "JSON"
        if (requireJson) {
            Map<String, String> format = new HashMap<>();
            format.put("type", "json_object");
            request.put("response_format", format);
            systemPrompt = systemPrompt + "\n\nYou MUST output valid JSON.";
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));
        
        request.put("messages", messages);
        request.put("temperature", 0.3);

        return request;
    }
}
