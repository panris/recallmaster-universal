package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import com.recallmaster.universal.util.JsonUtils;
import com.recallmaster.universal.util.SqlUtils;
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
    public String name() { return database.getName(); }

    @Override
    public String type() { return "postgres"; }

    @Override
    public boolean isAvailable() {
        return !database.getConnection().isBlank() && !database.getTable().isBlank();
    }

    @Override
    public List<SearchResult> search(SearchRequest request) {
        if (!isAvailable()) throw new IllegalStateException("PostgreSQL connector requires connection and table");
        String sql = buildSql(request.filters());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            String pgVector = SqlUtils.toPgVector(request.queryVector());
            statement.setString(index++, pgVector);
            for (String value : request.filters().values()) {
                statement.setString(index++, value);
            }
            statement.setString(index++, pgVector);
            statement.setInt(index, request.topK());
            try (ResultSet rs = statement.executeQuery()) {
                List<SearchResult> results = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> metadata = JsonUtils.parseFlatJson(rs.getString("metadata"));
                    results.add(new SearchResult(rs.getString("id"), rs.getString("text"), rs.getDouble("score"), metadata));
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("PostgreSQL pgvector search failed for " + name(), ex);
        }
    }

    @Override
    public void upsert(Collection<DocumentChunk> chunks) {
        if (!isAvailable()) throw new IllegalStateException("PostgreSQL connector requires connection and table");
        if (chunks.isEmpty()) return;
        String sql = buildUpsertSql();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (DocumentChunk chunk : chunks) {
                int index = 1;
                statement.setString(index++, chunk.id());
                statement.setString(index++, chunk.text());
                statement.setString(index++, SqlUtils.toPgVector(chunk.vector()));
                statement.setString(index, JsonUtils.toJson(chunk.metadata()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException ex) {
            throw new IllegalStateException("PostgreSQL pgvector upsert failed for " + name(), ex);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    private String buildSql(Map<String, String> filters) {
        String distance = switch (database.getMetric().toLowerCase()) {
            case "l2", "euclidean" -> "<->";
            case "inner_product", "ip" -> "<#>";
            default -> "<=>";
        };
        StringBuilder sql = new StringBuilder()
                .append("select ")
                .append(SqlUtils.quote(database.getIdCol())).append("::text as id, ")
                .append(SqlUtils.quote(database.getTextCol())).append("::text as text, ")
                .append("1 - (").append(SqlUtils.quote(database.getVectorCol())).append(" ")
                .append(distance).append(" ?::vector) as score, ")
                .append(SqlUtils.quote(database.getMetadataCol())).append("::text as metadata from ")
                .append(SqlUtils.qualified(database.getTable()));
        if (!filters.isEmpty()) {
            StringJoiner where = new StringJoiner(" and ");
            for (String key : filters.keySet()) { where.add(SqlUtils.quote(key) + "::text = ?"); }
            sql.append(" where ").append(where);
        }
        sql.append(" order by ").append(SqlUtils.quote(database.getVectorCol())).append(" ")
                .append(distance).append(" ?::vector limit ?");
        return sql.toString();
    }

    private String buildUpsertSql() {
        return "insert into " + SqlUtils.qualified(database.getTable()) +
                " (" + SqlUtils.quote(database.getIdCol()) + ", " +
                SqlUtils.quote(database.getTextCol()) + ", " +
                SqlUtils.quote(database.getVectorCol()) + ", " +
                SqlUtils.quote(database.getMetadataCol()) + ") " +
                "values (?, ?, ?::vector, ?::jsonb) " +
                "on conflict (" + SqlUtils.quote(database.getIdCol()) + ") do update set " +
                SqlUtils.quote(database.getTextCol()) + " = excluded." + SqlUtils.quote(database.getTextCol()) + ", " +
                SqlUtils.quote(database.getVectorCol()) + " = excluded." + SqlUtils.quote(database.getVectorCol()) + ", " +
                SqlUtils.quote(database.getMetadataCol()) + " = excluded." + SqlUtils.quote(database.getMetadataCol());
    }
}