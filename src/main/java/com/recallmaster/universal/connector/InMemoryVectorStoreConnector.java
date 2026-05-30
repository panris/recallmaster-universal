package com.recallmaster.universal.connector;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class InMemoryVectorStoreConnector implements VectorStoreConnector {

    private final String name;
    private final EmbeddingModel embeddingModel;
    private final List<Entry> entries = new ArrayList<>();

    public InMemoryVectorStoreConnector(RecallMasterProperties.Database database, EmbeddingModel embeddingModel) {
        this.name = database.getName();
        this.embeddingModel = embeddingModel;
        for (RecallMasterProperties.SeedDocument document : database.getDocuments()) {
            upsert(List.of(new DocumentChunk(document.getId(), document.getText(), null, document.getMetadata())));
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public synchronized List<SearchResult> search(SearchRequest request) {
        return entries.stream()
                .filter(entry -> matches(entry.chunk.metadata(), request.filters()))
                .map(entry -> new SearchResult(
                        entry.chunk.id(),
                        entry.chunk.text(),
                        VectorMath.cosine(request.queryVector(), entry.vector),
                        entry.chunk.metadata()))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(request.topK())
                .toList();
    }

    @Override
    public synchronized void upsert(Collection<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            entries.removeIf(entry -> entry.chunk.id().equals(chunk.id()));
            entries.add(new Entry(chunk, embeddingModel.embed(chunk.text())));
        }
    }

    private boolean matches(Map<String, String> metadata, Map<String, String> filters) {
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            if (!filter.getValue().equals(metadata.get(filter.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private record Entry(DocumentChunk chunk, float[] vector) {
    }
}
