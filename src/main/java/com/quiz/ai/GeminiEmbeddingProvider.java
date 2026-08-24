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

    private static final List<String> FALLBACK_EMBED_MODELS = List.of(
            "gemini-embedding-001",
            "gemini-embedding-2-preview",
            "gemini-embedding-2"
    );

    @Override
    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No AI API key provided for embeddings, using deterministic fallback embedding");
            return fallbackEmbedding(text);
        }

        String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;

        List<String> modelsToTry = new java.util.ArrayList<>();
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            modelsToTry.add(embeddingModel);
        }
        for (String fb : FALLBACK_EMBED_MODELS) {
            if (!modelsToTry.contains(fb)) {
                modelsToTry.add(fb);
            }
        }

        for (String targetModel : modelsToTry) {
            try {
                String modelPath = targetModel.startsWith("models/") ? targetModel : "models/" + targetModel;
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

                if (values.isArray() && !values.isEmpty()) {
                    float[] embedding = new float[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        embedding[i] = (float) values.get(i).asDouble();
                    }
                    return embedding;
                }
            } catch (Exception e) {
                log.warn("Gemini embedding call failed with model {}: {}", targetModel, e.getMessage());
            }
        }

        log.warn("All Gemini embedding models failed, using deterministic fallback vector for chunk");
        return fallbackEmbedding(text);
    }

    private float[] fallbackEmbedding(String text) {
        float[] vector = new float[dimensions > 0 ? dimensions : 3072];
        if (text == null || text.isBlank()) return vector;

        String[] words = text.toLowerCase().split("\\s+");
        for (String word : words) {
            int hash = Math.abs(word.hashCode()) % vector.length;
            vector[hash] += 1.0f;
        }

        // Normalize
        float norm = 0;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
