package com.recallmaster.universal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.connector.ConnectorHealthService;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.connector.InMemoryConnectorFactory;
import com.recallmaster.universal.connector.InMemoryVectorStoreConnector;
import com.recallmaster.universal.connector.VectorStoreConnector;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verify upsert API behavior: Memory connector supports upsert,
 * unsupported connectors throw UnsupportedOperationException.
 */
class ApiControllerUpsertTest {

    private HashEmbeddingModel embedding;
    private InMemoryVectorStoreConnector connector;

    @BeforeEach
    void setUp() {
        embedding = new HashEmbeddingModel(128);
        RecallMasterProperties.Database db = new RecallMasterProperties.Database();
        db.setName("test-memory");
        db.setType("memory");
        db.setDocuments(List.of());
        connector = new InMemoryVectorStoreConnector(db, embedding);
    }

    @Test
    void memoryConnectorUpsertAndSearch() {
        // Upsert documents
        DocumentChunk chunk1 = new DocumentChunk("doc-1", "负载均衡配置", null, Map.of("source", "test"));
        DocumentChunk chunk2 = new DocumentChunk("doc-2", "高可用心跳参数", null, Map.of());
        connector.upsert(List.of(chunk1, chunk2));

        // Search
        List<SearchResult> results = connector.search(new SearchRequest(
                "负载均衡", embedding.embed("负载均衡"), 5, Map.of()));
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).id()).isEqualTo("doc-1");
    }

    @Test
    void upsertWithServerGeneratedId() {
        // ApiController generates UUID when doc.id() is null, so the connector
        // always receives a non-null id. Simulate that flow.
        DocumentChunk chunk = new DocumentChunk("server-gen-id", "自动ID测试", null, Map.of());
        connector.upsert(List.of(chunk));

        List<SearchResult> results = connector.search(new SearchRequest(
                "自动ID", embedding.embed("自动ID"), 5, Map.of()));
        assertThat(results).extracting(SearchResult::id).contains("server-gen-id");
    }

    @Test
    void unsupportedConnectorThrowsOnUpsert() {
        // Create a stub connector that doesn't override upsert
        VectorStoreConnector unsupported = new VectorStoreConnector() {
            @Override public String name() { return "unsupported-test"; }
            @Override public String type() { return "stub"; }
            @Override public boolean isAvailable() { return true; }
            @Override public List<SearchResult> search(SearchRequest request) { return List.of(); }
        };

        assertThatThrownBy(() -> unsupported.upsert(List.of(
                DocumentChunk.of("id", "text"))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("stub connector does not support upsert");
    }
}
