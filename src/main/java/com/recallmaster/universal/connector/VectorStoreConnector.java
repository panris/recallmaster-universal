package com.recallmaster.universal.connector;

import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.util.Collection;
import java.util.List;

public interface VectorStoreConnector {

    String name();

    String type();

    boolean isAvailable();

    List<SearchResult> search(SearchRequest request);

    default void upsert(Collection<DocumentChunk> chunks) {
        throw new UnsupportedOperationException(type() + " connector does not support upsert");
    }
}
