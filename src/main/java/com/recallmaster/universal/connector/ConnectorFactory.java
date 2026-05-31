package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SPI factory for plugging in new VectorStoreConnector types
 * without modifying ConnectorRegistry.
 *
 * Register by adding {@code @Component} on the implementation class.
 */
public interface ConnectorFactory {

    /**
     * Whether this factory handles the given database type string.
     * Case-insensitive matching is recommended.
     */
    boolean supports(String type);

    /**
     * Create a connector instance for the given database config.
     */
    VectorStoreConnector create(RecallMasterProperties.Database database,
                                EmbeddingModelProvider embeddingModelProvider,
                                ObjectMapper objectMapper);
}
