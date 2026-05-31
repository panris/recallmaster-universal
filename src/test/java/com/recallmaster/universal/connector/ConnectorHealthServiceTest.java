package com.recallmaster.universal.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModelProvider;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class ConnectorHealthServiceTest {

    @Test
    void describesAndChecksMemoryConnector() {
        RecallMasterProperties properties = properties();
        HashEmbeddingModel embedding = new HashEmbeddingModel(128);
        EmbeddingModelProvider provider = () -> embedding;
        List<ConnectorFactory> factories = List.of(new InMemoryConnectorFactory());
        ConnectorRegistry registry = new ConnectorRegistry(properties, factories, provider, new ObjectMapper());
        ConnectorHealthService healthService = new ConnectorHealthService(registry, embedding, properties);

        ConnectorDescriptor descriptor = registry.describe("demo-memory");
        ConnectorHealth health = healthService.check("demo-memory");

        assertThat(descriptor.available()).isTrue();
        assertThat(descriptor.config()).containsEntry("documentCount", 1);
        assertThat(health.ok()).isTrue();
        assertThat(health.resultCount()).isEqualTo(1);
    }

    private RecallMasterProperties properties() {
        RecallMasterProperties properties = new RecallMasterProperties();
        RecallMasterProperties.Database database = new RecallMasterProperties.Database();
        database.setName("demo-memory");
        database.setType("memory");
        RecallMasterProperties.SeedDocument document = new RecallMasterProperties.SeedDocument();
        document.setId("doc_1");
        document.setText("负载均衡健康检查文档。");
        document.setMetadata(Map.of("topic", "lb"));
        database.setDocuments(List.of(document));
        properties.setDatabases(List.of(database));
        return properties;
    }
}
