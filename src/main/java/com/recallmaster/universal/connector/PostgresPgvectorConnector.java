package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class PostgresPgvectorConnector implements VectorStoreConnector {

    private final RecallMasterProperties.Database database;
    private final HikariDataSource dataSource;

    public PostgresPgvectorConnector(RecallMasterProperties.Database database) {
        this.database = database;
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(database.getConnection());
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setPoolName("pg-" + database.getName());
        this.dataSource = ds;
    }

    @Override
    public String name() {
        return database.getName();
    }

    @Override
    public String type() {
        return "postgres";
    }

    @Override
    public boolean isAvailable() {
        return !database.getConnection().isBlank() && !database.getTable().isBlank();
    }

    @Override
    public List<SearchResult> search(SearchRequest request) {
        if (!isAvailable()) {
            throw new IllegalStateException("PostgreSQL connector requires connection and table");
        }
        String sql = buildSql(request.filters());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            String pgVector = toPgVector(request.queryVector());
            statement.setString(index++, pgVector);
            for (String value : request.filters().values()) {
                statement.setString(index++, value);
            }
            statement.setString(index++, pgVector);
            statement.setInt(index, request.topK());
            try (ResultSet rs = statement.executeQuery()) {
                List<SearchResult> results = new ArrayList<>();
                while (rs.next()) {
                    String metaJson = rs.getString("metadata");
                    Map<String, String> metadata = parseMetadata(metaJson);
                    results.add(new SearchResult(
                            rs.getString("id"),
                            rs.getString("text"),
                            rs.getDouble("score"),
                            metadata));
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("PostgreSQL pgvector search failed for " + name(), ex);
        }
    }

    @Override
    public void upsert(Collection<DocumentChunk> chunks) {
        if (!isAvailable()) {
            throw new IllegalStateException("PostgreSQL connector requires connection and table");
        }
        if (chunks.isEmpty()) {
            return;
        }
        String sql = buildUpsertSql();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DocumentChunk chunk : chunks) {
                int index = 1;
                statement.setString(index++, chunk.id());
                statement.setString(index++, chunk.text());
                statement.setString(index++, toPgVector(chunk.vector()));
                statement.setString(index, toJson(chunk.metadata()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("PostgreSQL pgvector upsert failed for " + name(), ex);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private Map<String, String> parseMetadata(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        Map<String, String> map = new java.util.LinkedHashMap<>();
        // Simple JSON key:value parser for flat metadata
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return Map.copyOf(map);
    }

    // ... rest of private methods unchanged
    private String buildSql(Map<String, String> filters) {
        String distance = switch (database.getMetric().toLowerCase()) {
            case "l2", "euclidean" -> "<->";
            case "inner_product", "ip" -> "<#>";
            default -> "<=>";
        };
        StringBuilder sql = new StringBuilder()
                .append("select ")
                .append(quote(database.getIdCol())).append("::text as id, ")
                .append(quote(database.getTextCol())).append("::text as text, ")
                .append("1 - (").append(quote(database.getVectorCol())).append(" ")
                .append(distance).append(" ?::vector) as score, ")
                .append(quote(database.getMetadataCol())).append("::text as metadata from ")
                .append(qualified(database.getTable()));
        if (!filters.isEmpty()) {
            StringJoiner where = new StringJoiner(" and ");
            for (String key : filters.keySet()) {
                where.add(quote(key) + "::text = ?");
            }
            sql.append(" where ").append(where);
        }
        sql.append(" order by ").append(quote(database.getVectorCol())).append(" ")
                .append(distance).append(" ?::vector limit ?");
        return sql.toString();
    }

    private String toPgVector(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String qualified(String identifier) {
        String[] parts = identifier.split("\\.");
        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
            joiner.add(quote(part));
        }
        return joiner.toString();
    }

    private String buildUpsertSql() {
        return "insert into " + qualified(database.getTable()) +
                " (" + quote(database.getIdCol()) + ", " +
                quote(database.getTextCol()) + ", " +
                quote(database.getVectorCol()) + ", " +
                quote(database.getMetadataCol()) + ") " +
                "values (?, ?, ?::vector, ?::jsonb) " +
                "on conflict (" + quote(database.getIdCol()) + ") do update set " +
                quote(database.getTextCol()) + " = excluded." + quote(database.getTextCol()) + ", " +
                quote(database.getVectorCol()) + " = excluded." + quote(database.getVectorCol()) + ", " +
                quote(database.getMetadataCol()) + " = excluded." + quote(database.getMetadataCol());
    }

    private String toJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            joiner.add("\"" + escapeJson(entry.getKey()) + "\":\"" + escapeJson(entry.getValue()) + "\"");
        }
        return joiner.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}