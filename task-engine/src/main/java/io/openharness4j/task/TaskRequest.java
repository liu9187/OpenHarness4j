package io.openharness4j.task;

import java.util.Map;

public record TaskRequest(
        String type,
        Map<String, Object> input,
        Map<String, Object> metadata,
        long timeoutMillis
) {
    public TaskRequest {
        type = requireText(type, "type");
        input = input == null ? Map.of() : Map.copyOf(input);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timeoutMillis = Math.max(0, timeoutMillis);
    }

    public static TaskRequest of(String type, Map<String, Object> input) {
        return new TaskRequest(type, input, Map.of(), 0);
    }

    public static TaskRequest withTimeout(String type, Map<String, Object> input, long timeoutMillis) {
        return new TaskRequest(type, input, Map.of(), timeoutMillis);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
