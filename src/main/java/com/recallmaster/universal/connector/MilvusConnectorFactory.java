package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class MilvusConnectorFactory implements ConnectorFactory {

    @Override
    public boolean supports(String type) {
        return type.toLowerCase().equals("milvus");
    }

    @Override
    public VectorStoreConnector create(
            RecallMasterProperties.Database database,
            EmbeddingModelProvider embeddingModelProvider,
            ObjectMapper objectMapper) {
        return new MilvusRestConnector(database, objectMapper);
    }
}
