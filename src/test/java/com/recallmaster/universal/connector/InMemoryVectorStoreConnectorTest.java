package com.recallmaster.universal.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import com.recallmaster.universal.model.DocumentChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryVectorStoreConnectorTest {

    @Test
    void appliesMetadataFiltersAndRanksBySimilarity() {
        HashEmbeddingModel embedding = new HashEmbeddingModel(128);
        InMemoryVectorStoreConnector connector = new InMemoryVectorStoreConnector(database(), embedding);

        List<SearchResult> results = connector.search(new SearchRequest(
                "牙科根管治疗报销",
                embedding.embed("牙科根管治疗报销"),
                5,
                Map.of("topic", "dental")));

        assertThat(results).extracting(SearchResult::id).containsExactly("med_dental_limit");
    }

    @Test
    void upsertAddsNewDocumentAndSearchable() {
        HashEmbeddingModel embedding = new HashEmbeddingModel(128);
        InMemoryVectorStoreConnector connector = new InMemoryVectorStoreConnector(database(), embedding);

        // Upsert a new document
        connector.upsert(List.of(new DocumentChunk("new-doc", "新文档内容测试", null, Map.of("topic", "test"))));

        // Verify it's searchable
        List<SearchResult> results = connector.search(new SearchRequest(
                "新文档",
                embedding.embed("新文档"),
                5,
                Map.of()));
        assertThat(results).extracting(SearchResult::id).contains("new-doc");
    }

    @Test
    void upsertOverwritesExistingDocument() {
        HashEmbeddingModel embedding = new HashEmbeddingModel(128);
        InMemoryVectorStoreConnector connector = new InMemoryVectorStoreConnector(database(), embedding);

        // Upsert with existing id overwrites
        connector.upsert(List.of(new DocumentChunk("med_dental_limit", "牙科报销更新内容", null, Map.of("topic", "dental"))));

        List<SearchResult> results = connector.search(new SearchRequest(
                "牙科报销更新",
                embedding.embed("牙科报销更新"),
                5,
                Map.of()));
        assertThat(results).anySatisfy(r -> {
            assertThat(r.id()).isEqualTo("med_dental_limit");
            assertThat(r.text()).isEqualTo("牙科报销更新内容");
        });
    }

    private RecallMasterProperties.Database database() {
        RecallMasterProperties.Database database = new RecallMasterProperties.Database();
        database.setName("demo-memory");
        database.setType("memory");
        database.setDocuments(List.of(
                seed("med_dental_limit", "牙科报销年度上限为 2000 元，包含根管治疗。", "dental"),
                seed("tech_lb_config", "负载均衡需要配置 upstream 节点。", "lb")
        ));
        return database;
    }

    private RecallMasterProperties.SeedDocument seed(String id, String text, String topic) {
        RecallMasterProperties.SeedDocument seed = new RecallMasterProperties.SeedDocument();
        seed.setId(id);
        seed.setText(text);
        seed.setMetadata(Map.of("topic", topic));
        return seed;
    }
}
