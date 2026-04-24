package io.openharness4j.api;

import java.util.Map;

public record AgentContext(
        String sessionId,
        String userId,
        String traceId,
        Map<String, Object> metadata
) {
    public AgentContext {
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        traceId = requireText(traceId, "traceId");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
