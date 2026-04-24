package io.openharness4j.api;

import java.util.Map;

public record AgentRequest(
        String sessionId,
        String userId,
        String input,
        Map<String, Object> metadata
) {
    public AgentRequest {
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        input = requireText(input, "input");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentRequest of(String sessionId, String userId, String input) {
        return new AgentRequest(sessionId, userId, input, Map.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
