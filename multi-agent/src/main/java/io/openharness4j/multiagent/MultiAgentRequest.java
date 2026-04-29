package io.openharness4j.multiagent;

import java.util.Map;

public record MultiAgentRequest(
        String sessionId,
        String userId,
        String input,
        Map<String, Object> metadata
) {
    public MultiAgentRequest {
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        input = requireText(input, "input");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static MultiAgentRequest of(String sessionId, String userId, String input) {
        return new MultiAgentRequest(sessionId, userId, input, Map.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
