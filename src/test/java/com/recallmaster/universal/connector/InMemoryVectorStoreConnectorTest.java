package com.recallmaster.universal.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
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
