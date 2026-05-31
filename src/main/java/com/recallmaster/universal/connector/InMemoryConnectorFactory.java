package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class InMemoryConnectorFactory implements ConnectorFactory {

    @Override
    public boolean supports(String type) {
        String t = type.toLowerCase();
        return t.equals("memory") || t.equals("inmemory") || t.equals("demo");
    }

    @Override
    public VectorStoreConnector create(
            RecallMasterProperties.Database database,
            EmbeddingModelProvider embeddingModelProvider,
            ObjectMapper objectMapper) {
        EmbeddingModel embeddingModel = embeddingModelProvider.get();
        return new InMemoryVectorStoreConnector(database, embeddingModel);
    }
}
