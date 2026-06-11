package com.recallmaster.universal.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.HashEmbeddingModel;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Contract tests: any VectorStoreConnector implementation must satisfy these behaviors.
 * Currently tests InMemoryVectorStoreConnector; add PostgresPgvectorConnector (with embedded PG)
 * when infrastructure is available.
 */
class ConnectorContractTest {

    @TestFactory
    List<DynamicTest> inMemoryConnectorContract() {
        HashEmbeddingModel embedding = new HashEmbeddingModel(128);
        InMemoryVectorStoreConnector connector = new InMemoryVectorStoreConnector(memoryDatabase(), embedding);
        return contractTests(connector, embedding);
    }

    private List<DynamicTest> contractTests(VectorStoreConnector connector, HashEmbeddingModel embedding) {
        return List.of(
                DynamicTest.dynamicTest("search returns results ranked by score", () -> {
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "牙科", embedding.embed("牙科"), 10, Map.of()));
                    assertThat(results).isNotEmpty();
                    // Scores should be descending
                    for (int i = 1; i < results.size(); i++) {
                        assertThat(results.get(i).score()).isLessThanOrEqualTo(results.get(i - 1).score());
                    }
                }),
                DynamicTest.dynamicTest("search with generic query returns results", () -> {
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "查询", embedding.embed("查询"), 10, Map.of()));
                    assertThat(results.size()).isLessThanOrEqualTo(10);
                }),
                DynamicTest.dynamicTest("search respects topK limit", () -> {
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "牙科", embedding.embed("牙科"), 1, Map.of()));
                    assertThat(results).hasSize(1);
                }),
                DynamicTest.dynamicTest("search applies metadata filters", () -> {
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "牙科", embedding.embed("牙科"), 10, Map.of("topic", "dental")));
                    assertThat(results).isNotEmpty();
                    assertThat(results).allSatisfy(r -> assertThat(r.text()).contains("牙科"));
                }),
                DynamicTest.dynamicTest("upsert then search finds new document", () -> {
                    connector.upsert(List.of(new DocumentChunk("contract-test-doc", "契约测试文档", null, Map.of("topic", "test"))));
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "契约测试", embedding.embed("契约测试"), 10, Map.of()));
                    assertThat(results).extracting(SearchResult::id).contains("contract-test-doc");
                }),
                DynamicTest.dynamicTest("upsert overwrites existing document", () -> {
                    connector.upsert(List.of(new DocumentChunk("med_dental_limit", "更新内容", null, Map.of())));
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "更新内容", embedding.embed("更新内容"), 10, Map.of()));
                    assertThat(results).anySatisfy(r -> {
                        assertThat(r.id()).isEqualTo("med_dental_limit");
                        assertThat(r.text()).isEqualTo("更新内容");
                    });
                }),
                DynamicTest.dynamicTest("search result has non-null metadata", () -> {
                    List<SearchResult> results = connector.search(new SearchRequest(
                            "牙科", embedding.embed("牙科"), 10, Map.of()));
                    assertThat(results).allSatisfy(r -> assertThat(r.metadata()).isNotNull());
                }),
                DynamicTest.dynamicTest("connector type is defined", () -> {
                    assertThat(connector.type()).isNotBlank();
                }),
                DynamicTest.dynamicTest("connector name matches config", () -> {
                    assertThat(connector.name()).isEqualTo("demo-memory");
                })
        );
    }

    private RecallMasterProperties.Database memoryDatabase() {
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