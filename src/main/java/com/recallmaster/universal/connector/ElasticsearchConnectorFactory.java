package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchConnectorFactory implements ConnectorFactory {

    @Override
    public boolean supports(String type) {
        String t = type.toLowerCase();
        return t.equals("elasticsearch") || t.equals("elastic") || t.equals("es");
    }

    @Override
    public VectorStoreConnector create(
            RecallMasterProperties.Database database,
            EmbeddingModelProvider embeddingModelProvider,
            ObjectMapper objectMapper) {
        return new ElasticsearchConnector(database, objectMapper);
    }
}
