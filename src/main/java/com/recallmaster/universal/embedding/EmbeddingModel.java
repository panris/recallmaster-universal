package com.recallmaster.universal.embedding;

public interface EmbeddingModel {

    String name();

    float[] embed(String text);
}
