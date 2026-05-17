package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConnectorHealthService {

    private static final String SAMPLE_QUERY = "RecallMaster health check";

    private final ConnectorRegistry connectorRegistry;
    private final EmbeddingModel embeddingModel;
    private final RecallMasterProperties properties;

    public ConnectorHealthService(
            ConnectorRegistry connectorRegistry,
            EmbeddingModel embeddingModel,
            RecallMasterProperties properties
    ) {
        this.connectorRegistry = connectorRegistry;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public List<ConnectorHealth> checkAll() {
        return connectorRegistry.all().stream()
                .map(connector -> check(connector.name()))
                .toList();
    }

    public ConnectorHealth check(String name) {
        VectorStoreConnector connector = connectorRegistry.get(name);
        Instant started = Instant.now();
        if (!connector.isAvailable()) {
            return new ConnectorHealth(
                    connector.name(),
                    connector.type(),
                    false,
                    false,
                    0,
                    0,
                    List.of(),
                    "连接器配置不完整，请先补齐必要字段。",
                    Instant.now());
        }
        try {
            SearchRequest request = new SearchRequest(
                    SAMPLE_QUERY,
                    embeddingModel.embed(SAMPLE_QUERY),
                    Math.min(3, Math.max(1, properties.getDefaultTopK())),
                    Map.of());
            List<SearchResult> results = connector.search(request);
            long latency = Duration.between(started, Instant.now()).toMillis();
            return new ConnectorHealth(
                    connector.name(),
                    connector.type(),
                    true,
                    true,
                    latency,
                    results.size(),
                    results.stream().map(SearchResult::id).toList(),
                    "健康检查通过。",
                    Instant.now());
        } catch (RuntimeException ex) {
            long latency = Duration.between(started, Instant.now()).toMillis();
            return new ConnectorHealth(
                    connector.name(),
                    connector.type(),
                    true,
                    false,
                    latency,
                    0,
                    List.of(),
                    rootMessage(ex),
                    Instant.now());
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getClass().getSimpleName() : current.getMessage();
    }
}
