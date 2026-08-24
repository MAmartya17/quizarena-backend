package com.quiz.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of EmbeddingProvider.
 * Uses the text-embedding-004 model via REST API.
 */
@Slf4j
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    private final String apiKey;
    private final String embeddingModel;
    private final int dimensions;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    public GeminiEmbeddingProvider(String apiKey, String embeddingModel, int dimensions) {
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.dimensions = dimensions;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] embed(String text) {
        try {
            // Truncate very long text (embedding models have token limits)
            String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;

            String modelPath = embeddingModel.startsWith("models/") ? embeddingModel : "models/" + embeddingModel;
            Map<String, Object> requestBody = Map.of(
                    "model", modelPath,
                    "content", Map.of("parts", List.of(Map.of("text", truncated)))
            );

            String url = "/" + modelPath + ":embedContent?key=" + apiKey;

            String responseStr = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode values = root.path("embedding").path("values");

            if (!values.isArray() || values.isEmpty()) {
                throw new RuntimeException("No embedding values in Gemini response");
            }

            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = (float) values.get(i).asDouble();
            }

            return embedding;

        } catch (Exception e) {
            log.error("Gemini embedding call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
