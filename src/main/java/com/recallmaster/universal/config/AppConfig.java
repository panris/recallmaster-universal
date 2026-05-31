package com.recallmaster.universal.config;

import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.embedding.OpenAiCompatibleEmbeddingModel;
import com.recallmaster.universal.llm.LlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class AppConfig {

    @Bean
    public EmbeddingModel embeddingModel(RecallMasterProperties properties, ObjectMapper objectMapper) {
        String provider = properties.getEmbedding().getProvider();
        if ("hash".equalsIgnoreCase(provider)) {
            return new HashEmbeddingModel(properties.getEmbedding().getDimensions());
        }
        if ("openai".equalsIgnoreCase(provider) || "openai-compatible".equalsIgnoreCase(provider)) {
            return new OpenAiCompatibleEmbeddingModel(
                    properties.getEmbedding().getModel(),
                    properties.getEmbedding().getBaseUrl(),
                    properties.getEmbedding().getApiKey(),
                    properties.getEmbedding().getDimensions(),
                    objectMapper);
        }
        throw new IllegalArgumentException("Unsupported embedding provider: " + provider + ". Supported: hash, openai, openai-compatible");
    }

    @Bean
    public EmbeddingModelProvider embeddingModelProvider(EmbeddingModel embeddingModel) {
        return () -> embeddingModel;
    }

    @Bean
    public LlmClient llmClient(RecallMasterProperties properties, ObjectMapper objectMapper) {
        return new LlmClient(
                "gpt-4o-mini",
                properties.getEvaluator().getBaseUrl(),
                properties.getEvaluator().getApiKey(),
                objectMapper);
    }
}
