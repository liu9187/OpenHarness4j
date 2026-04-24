package io.openharness4j.api;

import java.util.Map;

public record ToolContext(
        String sessionId,
        String userId,
        String traceId,
        String toolCallId,
        Map<String, Object> args,
        Map<String, Object> metadata
) {
    public ToolContext {
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        traceId = requireText(traceId, "traceId");
        toolCallId = requireText(toolCallId, "toolCallId");
        args = args == null ? Map.of() : Map.copyOf(args);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
