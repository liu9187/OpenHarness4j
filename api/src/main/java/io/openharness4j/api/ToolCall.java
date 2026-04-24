package io.openharness4j.api;

import java.util.Map;
import java.util.UUID;

public record ToolCall(
        String id,
        String name,
        Map<String, Object> args
) {
    public ToolCall {
        id = id == null || id.isBlank() ? "call_" + UUID.randomUUID() : id;
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        args = args == null ? Map.of() : Map.copyOf(args);
    }

    public static ToolCall of(String name, Map<String, Object> args) {
        return new ToolCall(null, name, args);
    }
}
