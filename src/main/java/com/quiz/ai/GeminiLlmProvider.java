package com.quiz.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of LlmProvider.
 * Uses the Gemini REST API (generativelanguage.googleapis.com).
 */
@Slf4j
public class GeminiLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String model;
    private final int maxRetries;
    private final int timeoutSeconds;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    public GeminiLlmProvider(String apiKey, String model, int maxRetries, int timeoutSeconds) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxRetries = maxRetries;
        this.timeoutSeconds = timeoutSeconds;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        return callGemini(systemPrompt, userPrompt, null);
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        return callGemini(systemPrompt, userPrompt, "application/json");
    }

    private String callGemini(String systemPrompt, String userPrompt, String responseMimeType) {
        int attempt = 0;
        long delay = 1000;

        while (true) {
            attempt++;
            try {
                Map<String, Object> requestBody = buildRequest(systemPrompt, userPrompt, responseMimeType);
                String modelPath = model.startsWith("models/") ? model : "models/" + model;
                String url = "/" + modelPath + ":generateContent?key=" + apiKey;

                String responseStr = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(responseStr);
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode content = candidates.get(0).path("content").path("parts");
                    if (content.isArray() && !content.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode part : content) {
                            if (part.has("text") && !part.has("thought")) {
                                sb.append(part.path("text").asText(""));
                            }
                        }
                        if (sb.length() > 0) {
                            return sb.toString();
                        }
                        return content.get(0).path("text").asText("");
                    }
                }

                // Check for blocked content
                JsonNode blockReason = root.path("promptFeedback").path("blockReason");
                if (!blockReason.isMissingNode()) {
                    throw new RuntimeException("Content blocked by Gemini: " + blockReason.asText());
                }

                throw new RuntimeException("Unexpected Gemini response format");

            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    log.error("Gemini API call failed after {} attempts: {}", attempt, e.getMessage());
                    throw new RuntimeException("AI service unavailable after " + attempt + " attempts", e);
                }
                log.warn("Gemini API call attempt {} failed, retrying in {}ms: {}", attempt, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
                delay *= 2; // exponential backoff
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequest(String systemPrompt, String userPrompt, String responseMimeType) {
        Map<String, Object> request = new java.util.HashMap<>();

        // System instruction
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            request.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }

        // User content
        request.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))
        ));

        // Generation config
        Map<String, Object> genConfig = new java.util.HashMap<>();
        genConfig.put("temperature", 0.3);
        genConfig.put("maxOutputTokens", 8192);

        if (responseMimeType != null) {
            genConfig.put("responseMimeType", responseMimeType);
        }

        request.put("generationConfig", genConfig);

        return request;
    }
}
