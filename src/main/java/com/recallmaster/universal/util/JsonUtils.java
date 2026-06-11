package com.recallmaster.universal.util;

import java.util.Map;
import java.util.StringJoiner;

public final class JsonUtils {

    public static String toJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            joiner.add("\"" + escapeJson(entry.getKey()) + "\":\"" + escapeJson(entry.getValue()) + "\"");
        }
        return joiner.toString();
    }

    public static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static Map<String, String> parseFlatJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Map.of();
        }
        Map<String, String> map = new java.util.LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return Map.copyOf(map);
    }

    private JsonUtils() {}
}