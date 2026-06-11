package com.recallmaster.universal.web;

import com.recallmaster.universal.connector.ConnectorDescriptor;
import com.recallmaster.universal.connector.ConnectorHealth;
import com.recallmaster.universal.connector.ConnectorHealthService;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.connector.VectorStoreConnector;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.model.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorRegistry connectorRegistry;
    private final ConnectorHealthService connectorHealthService;
    private final EmbeddingModel embeddingModel;

    public ConnectorController(ConnectorRegistry connectorRegistry,
                               ConnectorHealthService connectorHealthService,
                               EmbeddingModel embeddingModel) {
        this.connectorRegistry = connectorRegistry;
        this.connectorHealthService = connectorHealthService;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping
    public List<ConnectorDescriptor> listConnectors() {
        return connectorRegistry.describeAll();
    }

    @GetMapping("/{name}")
    public ConnectorDescriptor getConnector(@PathVariable String name) {
        return connectorRegistry.describe(name);
    }

    @PostMapping("/health")
    public List<ConnectorHealth> checkAllConnectors() {
        return connectorHealthService.checkAll();
    }

    @PostMapping("/{name}/health")
    public ConnectorHealth checkConnector(@PathVariable String name) {
        return connectorHealthService.check(name);
    }

    @PostMapping("/{name}/upsert")
    public void upsert(@PathVariable String name, @RequestBody UpsertRequest request) {
        VectorStoreConnector connector = connectorRegistry.get(name);
        if (connector == null) {
            throw new IllegalArgumentException("Unknown connector: " + name);
        }
        List<DocumentChunk> chunks = request.documents().stream()
                .map(doc -> {
                    String id = doc.id() != null ? doc.id() : UUID.randomUUID().toString();
                    float[] vector = embeddingModel.embed(doc.text());
                    return new DocumentChunk(id, doc.text(), vector, doc.metadata());
                })
                .toList();
        connector.upsert(chunks);
    }
}