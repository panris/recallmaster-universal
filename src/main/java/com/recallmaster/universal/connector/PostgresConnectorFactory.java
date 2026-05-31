package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PostgresConnectorFactory implements ConnectorFactory {

    @Override
    public boolean supports(String type) {
        String t = type.toLowerCase();
        return t.equals("postgres") || t.equals("postgresql") || t.equals("pgvector");
    }

    @Override
    public VectorStoreConnector create(
            RecallMasterProperties.Database database,
            EmbeddingModelProvider embeddingModelProvider,
            ObjectMapper objectMapper) {
        return new PostgresPgvectorConnector(database);
    }
}
