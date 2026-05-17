package com.recallmaster.universal.config;

import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public EmbeddingModel embeddingModel(RecallMasterProperties properties) {
        if (!"hash".equalsIgnoreCase(properties.getEmbedding().getProvider())) {
            throw new IllegalArgumentException("Only hash embedding is built in. Configure external embedding through a custom bean.");
        }
        return new HashEmbeddingModel(properties.getEmbedding().getDimensions());
    }
}
