package com.recallmaster.universal.util;

import java.util.StringJoiner;

public final class SqlUtils {

    public static String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public static String qualified(String identifier) {
        String[] parts = identifier.split("\\.");
        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
            joiner.add(quote(part));
        }
        return joiner.toString();
    }

    public static String toPgVector(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private SqlUtils() {}
}