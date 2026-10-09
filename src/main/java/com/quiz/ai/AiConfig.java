package com.quiz.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for AI services — creates the provider beans based on env vars.
 */
@Configuration
@EnableAsync
public class AiConfig {

    @Value("${app.ai.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.ai.groq.model:llama3-70b-8192}")
    private String groqModel;

    @Value("${app.ai.embedding-model:gemini-embedding-001}")
    private String embeddingModel;

    @Value("${app.ai.embedding-dimensions:3072}")
    private int embeddingDimensions;

    @Value("${app.ai.max-retries:3}")
    private int maxRetries;

    @Value("${app.ai.timeout-seconds:60}")
    private int timeoutSeconds;

    @Bean
    public LlmProvider llmProvider() {
        // Fall back to Gemini if no Groq API key is provided
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return new GeminiLlmProvider(geminiApiKey, "gemini-flash-lite-latest", maxRetries, timeoutSeconds);
        }
        return new GroqLlmProvider(groqApiKey, groqModel, maxRetries);
    }

    @Bean
    public EmbeddingProvider embeddingProvider() {
        return new GeminiEmbeddingProvider(geminiApiKey, embeddingModel, embeddingDimensions);
    }

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("ai-task-");
        executor.initialize();
        return executor;
    }
}
