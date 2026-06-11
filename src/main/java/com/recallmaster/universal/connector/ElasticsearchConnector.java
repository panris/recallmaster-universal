package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ElasticsearchConnector implements VectorStoreConnector {

    private final RecallMasterProperties.Database database;
    private final HttpJsonClient http;
    private final ObjectMapper objectMapper;

    public ElasticsearchConnector(RecallMasterProperties.Database database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
        this.http = new HttpJsonClient(objectMapper);
    }

    @Override
    public String name() {
        return database.getName();
    }

    @Override
    public String type() {
        return "elasticsearch";
    }

    @Override
    public boolean isAvailable() {
        return !database.getUri().isBlank() && !database.getIndex().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchResult> search(SearchRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("Elasticsearch connector requires uri and index");
        }
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", database.getVectorCol());
        knn.put("query_vector", toList(request.queryVector()));
        knn.put("k", request.topK());
        knn.put("num_candidates", Math.max(50, request.topK() * 10));
        if (!request.filters().isEmpty()) {
            List<Map<String, Object>> filters = request.filters().entrySet().stream()
                    .map(entry -> Map.<String, Object>of("term", Map.of(entry.getKey(), entry.getValue())))
                    .toList();
            knn.put("filter", filters);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("knn", knn);
        body.put("_source", List.of(database.getIdCol(), database.getTextCol(), database.getMetadataCol()));
        Map<String, Object> response = http.postJson(
                trimTrailingSlash(database.getUri()) + "/" + database.getIndex() + "/_search",
                body,
                database.getApiKey());
        Map<String, Object> hitsNode = (Map<String, Object>) response.getOrDefault("hits", Map.of());
        List<Map<String, Object>> hits = (List<Map<String, Object>>) hitsNode.getOrDefault("hits", List.of());
        List<SearchResult> results = new ArrayList<>();
        for (Map<String, Object> hit : hits) {
            Map<String, Object> source = (Map<String, Object>) hit.getOrDefault("_source", Map.of());
            String id = String.valueOf(source.getOrDefault(database.getIdCol(), hit.getOrDefault("_id", "")));
            String text = String.valueOf(source.getOrDefault(database.getTextCol(), ""));
            double score = ((Number) hit.getOrDefault("_score", 0.0)).doubleValue();
            Map<String, String> metadata = flatten(source.get(database.getMetadataCol()));
            results.add(new SearchResult(id, text, score, metadata));
        }
        return results;
    }

    private List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float v : vector) {
            values.add(v);
        }
        return values;
    }

    private Map<String, String> flatten(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Override
    public void upsert(Collection<DocumentChunk> chunks) {
        if (!isAvailable()) {
            throw new IllegalStateException("Elasticsearch connector requires uri and index");
        }
        String bulkUrl = trimTrailingSlash(database.getUri()) + "/" + database.getIndex() + "/_bulk";
        List<String> lines = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> action = Map.of("index", Map.of(
                    "_id", chunk.id(),
                    "_index", database.getIndex()));
            try {
                lines.add(objectMapper.writeValueAsString(action));
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put(database.getIdCol(), chunk.id());
                doc.put(database.getTextCol(), chunk.text());
                if (chunk.vector() != null && chunk.vector().length > 0) {
                    doc.put(database.getVectorCol(), toList(chunk.vector()));
                }
                if (chunk.metadata() != null && !chunk.metadata().isEmpty()) {
                    doc.put(database.getMetadataCol(), chunk.metadata());
                }
                lines.add(objectMapper.writeValueAsString(doc));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize chunk " + chunk.id(), e);
            }
        }
        String ndjson = String.join("\n", lines) + "\n";
        http.postNdjson(bulkUrl, ndjson, database.getApiKey());
    }
}
