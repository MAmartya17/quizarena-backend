package com.quiz.ai;

/**
 * Abstraction over embedding providers.
 */
public interface EmbeddingProvider {

    /**
     * Generate an embedding vector for the given text.
     *
     * @param text the text to embed
     * @return float array representing the embedding vector
     */
    float[] embed(String text);

    /**
     * Get the dimensionality of the embedding vectors.
     */
    int dimensions();
}
