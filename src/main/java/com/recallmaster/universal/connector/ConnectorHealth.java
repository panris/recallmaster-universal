package com.recallmaster.universal.connector;

import java.time.Instant;
import java.util.List;

public record ConnectorHealth(
        String name,
        String type,
        boolean available,
        boolean ok,
        long latencyMs,
        int resultCount,
        List<String> sampleIds,
        String message,
        Instant checkedAt
) {
    public ConnectorHealth {
        sampleIds = sampleIds == null ? List.of() : List.copyOf(sampleIds);
        message = message == null ? "" : message;
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }
}
