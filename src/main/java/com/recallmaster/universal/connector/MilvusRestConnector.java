package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import tools.jackson.databind.ObjectMapper;

public class MilvusRestConnector implements VectorStoreConnector {

    private final RecallMasterProperties.Database database;
    private final HttpJsonClient http;

    public MilvusRestConnector(RecallMasterProperties.Database database, ObjectMapper objectMapper) {
        this.database = database;
        this.http = new HttpJsonClient(objectMapper);
    }

    @Override
    public String name() {
        return database.getName();
    }

    @Override
    public String type() {
        return "milvus";
    }

    @Override
    public boolean isAvailable() {
        return !database.getUri().isBlank() && !database.getCollection().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchResult> search(SearchRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("Milvus connector requires uri and collection");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", database.getCollection());
        body.put("data", List.of(toList(request.queryVector())));
        body.put("limit", request.topK());
        body.put("annsField", database.getVectorCol());
        body.put("outputFields", List.of(database.getIdCol(), database.getTextCol(), database.getMetadataCol()));
        if (!request.filters().isEmpty()) {
            body.put("filter", toMilvusFilter(request.filters()));
        }
        String endpoint = database.getParams().getOrDefault(
                "searchEndpoint",
                trimTrailingSlash(database.getUri()) + "/v2/vectordb/entities/search");
        Map<String, Object> response = http.postJson(endpoint, body, database.getApiKey());
        Object rawData = response.get("data");
        if (!(rawData instanceof List<?> list)) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                row.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String id = String.valueOf(row.getOrDefault(database.getIdCol(), row.getOrDefault("id", "")));
            String text = String.valueOf(row.getOrDefault(database.getTextCol(), ""));
            double score = ((Number) row.getOrDefault("distance", row.getOrDefault("score", 0.0))).doubleValue();
            Map<String, String> metadata = flatten(row.get(database.getMetadataCol()));
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

    private String toMilvusFilter(Map<String, String> filters) {
        StringJoiner joiner = new StringJoiner(" && ");
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            joiner.add(entry.getKey() + " == \"" + entry.getValue().replace("\"", "\\\"") + "\"");
        }
        return joiner.toString();
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
}
