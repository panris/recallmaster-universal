package com.recallmaster.universal.connector;

import java.util.Map;

public record ConnectorDescriptor(
        String name,
        String type,
        boolean available,
        Map<String, Object> config,
        Map<String, String> hints
) {
    public ConnectorDescriptor {
        config = config == null ? Map.of() : Map.copyOf(config);
        hints = hints == null ? Map.of() : Map.copyOf(hints);
    }
}
