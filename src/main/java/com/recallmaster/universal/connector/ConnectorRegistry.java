package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModel;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ConnectorRegistry {

    private final Map<String, VectorStoreConnector> connectors = new LinkedHashMap<>();
    private final Map<String, RecallMasterProperties.Database> databases = new LinkedHashMap<>();

    public ConnectorRegistry(
            RecallMasterProperties properties,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper
    ) {
        for (RecallMasterProperties.Database database : properties.getDatabases()) {
            if (!database.isEnabled()) {
                continue;
            }
            VectorStoreConnector connector = create(database, embeddingModel, objectMapper);
            connectors.put(connector.name(), connector);
            databases.put(connector.name(), database);
        }
    }

    public Collection<VectorStoreConnector> all() {
        return connectors.values();
    }

    public VectorStoreConnector get(String name) {
        VectorStoreConnector connector = connectors.get(name);
        if (connector == null) {
            throw new IllegalArgumentException("Unknown database connector: " + name);
        }
        return connector;
    }

    public RecallMasterProperties.Database config(String name) {
        RecallMasterProperties.Database database = databases.get(name);
        if (database == null) {
            throw new IllegalArgumentException("Unknown database connector: " + name);
        }
        return database;
    }

    public List<ConnectorDescriptor> describeAll() {
        return connectors.values().stream()
                .map(this::describe)
                .toList();
    }

    public ConnectorDescriptor describe(String name) {
        return describe(get(name));
    }

    public String defaultName() {
        return connectors.keySet().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No vector database connector configured"));
    }

    private VectorStoreConnector create(
            RecallMasterProperties.Database database,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper
    ) {
        return switch (database.getType().toLowerCase()) {
            case "memory", "inmemory", "demo" -> new InMemoryVectorStoreConnector(database, embeddingModel);
            case "postgres", "postgresql", "pgvector" -> new PostgresPgvectorConnector(database);
            case "milvus" -> new MilvusRestConnector(database, objectMapper);
            case "chroma", "chromadb" -> new ChromaConnector(database, objectMapper);
            case "elasticsearch", "elastic", "es" -> new ElasticsearchConnector(database, objectMapper);
            default -> throw new IllegalArgumentException("Unsupported connector type: " + database.getType());
        };
    }

    private ConnectorDescriptor describe(VectorStoreConnector connector) {
        RecallMasterProperties.Database database = databases.get(connector.name());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("collection", emptyAsDash(database.getCollection()));
        config.put("index", emptyAsDash(database.getIndex()));
        config.put("table", emptyAsDash(database.getTable()));
        config.put("idCol", database.getIdCol());
        config.put("textCol", database.getTextCol());
        config.put("vectorCol", database.getVectorCol());
        config.put("metadataCol", database.getMetadataCol());
        config.put("dimension", database.getDimension());
        config.put("metric", database.getMetric());
        config.put("documentCount", database.getDocuments().size());
        config.put("uriConfigured", !database.getUri().isBlank());
        config.put("connectionConfigured", !database.getConnection().isBlank());
        config.put("apiKeyConfigured", !database.getApiKey().isBlank());
        return new ConnectorDescriptor(
                connector.name(),
                connector.type(),
                connector.isAvailable(),
                config,
                hints(database));
    }

    private Map<String, String> hints(RecallMasterProperties.Database database) {
        Map<String, String> hints = new LinkedHashMap<>();
        String type = database.getType().toLowerCase();
        if (type.contains("postgres") || type.contains("pgvector")) {
            hints.put("required", "connection, table, idCol, textCol, vectorCol");
            hints.put("quickCheck", "select id, text from table order by embedding <=> query_vector limit topK");
        } else if (type.contains("milvus")) {
            hints.put("required", "uri, collection, idCol, textCol, vectorCol");
            hints.put("quickCheck", "POST /v2/vectordb/entities/search");
        } else if (type.contains("chroma")) {
            hints.put("required", "uri, collection");
            hints.put("quickCheck", "POST collection query endpoint");
        } else if (type.contains("elastic") || type.equals("es")) {
            hints.put("required", "uri, index, vectorCol");
            hints.put("quickCheck", "POST /{index}/_search with knn");
        } else {
            hints.put("required", "documents");
            hints.put("quickCheck", "in-memory cosine search");
        }
        return hints;
    }

    private String emptyAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
