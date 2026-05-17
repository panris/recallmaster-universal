package com.recallmaster.universal.embedding;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public class HashEmbeddingModel implements EmbeddingModel {

    private static final int DEFAULT_DIMENSIONS = 256;
    private final int dimensions;

    public HashEmbeddingModel() {
        this(DEFAULT_DIMENSIONS);
    }

    public HashEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public String name() {
        return "hash-embedding-v1";
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimensions];
        for (String token : tokenize(text)) {
            int h1 = fnv1a(token);
            int h2 = fnv1a(new StringBuilder(token).reverse().toString());
            int index = Math.floorMod(h1, dimensions);
            vector[index] += (Math.floorMod(h2, 2) == 0) ? 1.0f : -1.0f;
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        for (int offset = 0; offset < normalized.length(); ) {
            int cp = normalized.codePointAt(offset);
            if (Character.isLetterOrDigit(cp) && cp < 128) {
                ascii.appendCodePoint(cp);
            } else {
                flush(ascii, tokens);
                if (!Character.isWhitespace(cp) && !Character.isISOControl(cp)) {
                    tokens.add(new String(Character.toChars(cp)));
                }
            }
            offset += Character.charCount(cp);
        }
        flush(ascii, tokens);
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String current = tokens.get(i);
            String next = tokens.get(i + 1);
            if (current.length() == 1 && next.length() == 1) {
                tokens.add(current + next);
            }
        }
        return tokens;
    }

    private void flush(StringBuilder ascii, List<String> tokens) {
        if (!ascii.isEmpty()) {
            String value = ascii.toString();
            tokens.add(value);
            if (value.length() > 4) {
                for (int i = 0; i + 3 <= value.length(); i++) {
                    tokens.add(value.substring(i, i + 3));
                }
            }
            ascii.setLength(0);
        }
    }

    private int fnv1a(String token) {
        int hash = 0x811c9dc5;
        for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
