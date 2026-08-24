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
    private String apiKey;

    @Value("${app.ai.model:gemini-2.0-flash}")
    private String model;

    @Value("${app.ai.embedding-model:text-embedding-004}")
    private String embeddingModel;

    @Value("${app.ai.embedding-dimensions:768}")
    private int embeddingDimensions;

    @Value("${app.ai.max-retries:3}")
    private int maxRetries;

    @Value("${app.ai.timeout-seconds:60}")
    private int timeoutSeconds;

    @Bean
    public LlmProvider llmProvider() {
        return new GeminiLlmProvider(apiKey, model, maxRetries, timeoutSeconds);
    }

    @Bean
    public EmbeddingProvider embeddingProvider() {
        return new GeminiEmbeddingProvider(apiKey, embeddingModel, embeddingDimensions);
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
