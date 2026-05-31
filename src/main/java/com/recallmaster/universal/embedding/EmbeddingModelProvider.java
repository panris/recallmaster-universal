package com.recallmaster.universal.embedding;

/**
 * Provider interface so ConnectorFactory implementations can
 * optionally obtain the configured EmbeddingModel without
 * hard-coding a specific instance.
 */
@FunctionalInterface
public interface EmbeddingModelProvider {
    EmbeddingModel get();
}
