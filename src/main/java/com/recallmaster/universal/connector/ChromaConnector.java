package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

public class ChromaConnector implements VectorStoreConnector {

    private final RecallMasterProperties.Database database;
    private final HttpJsonClient http;

    public ChromaConnector(RecallMasterProperties.Database database, ObjectMapper objectMapper) {
        this.database = database;
        this.http = new HttpJsonClient(objectMapper);
    }

    @Override
    public String name() {
        return database.getName();
    }

    @Override
    public String type() {
        return "chroma";
    }

    @Override
    public boolean isAvailable() {
        return !database.getUri().isBlank() && !database.getCollection().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchResult> search(SearchRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("Chroma connector requires uri and collection");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(toList(request.queryVector())));
        body.put("n_results", request.topK());
        body.put("include", List.of("documents", "metadatas", "distances"));
        if (!request.filters().isEmpty()) {
            body.put("where", request.filters());
        }
        String base = trimTrailingSlash(database.getUri());
        String tenant = database.getParams().getOrDefault("tenant", "default_tenant");
        String db = database.getParams().getOrDefault("database", "default_database");
        String path = database.getCollection().startsWith("http")
                ? database.getCollection()
                : base + "/api/v2/tenants/" + tenant + "/databases/" + db + "/collections/"
                + database.getCollection() + "/query";
        Map<String, Object> response = http.postJson(path, body, database.getApiKey());
        List<List<String>> ids = (List<List<String>>) response.getOrDefault("ids", List.of());
        List<List<String>> documents = (List<List<String>>) response.getOrDefault("documents", List.of());
        List<List<Map<String, String>>> metadatas =
                (List<List<Map<String, String>>>) response.getOrDefault("metadatas", List.of());
        List<List<Number>> distances = (List<List<Number>>) response.getOrDefault("distances", List.of());
        List<SearchResult> results = new ArrayList<>();
        if (ids.isEmpty()) {
            return results;
        }
        for (int i = 0; i < ids.getFirst().size(); i++) {
            String id = ids.getFirst().get(i);
            String text = getNested(documents, i, "");
            Map<String, String> metadata = getNested(metadatas, i, Map.of());
            double distance = getNested(distances, i, 1.0).doubleValue();
            results.add(new SearchResult(id, text, 1.0 - distance, metadata));
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

    private <T> T getNested(List<List<T>> nested, int index, T defaultValue) {
        if (nested == null || nested.isEmpty() || nested.getFirst() == null || nested.getFirst().size() <= index) {
            return defaultValue;
        }
        T value = nested.getFirst().get(index);
        return value == null ? defaultValue : value;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
