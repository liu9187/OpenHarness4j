package io.openharness4j.personal.team;

import java.util.Map;

public record TeamAgentRequest(
        String agentId,
        String sessionId,
        String userId,
        String instruction,
        Map<String, Object> metadata,
        long timeoutMillis
) {
    public TeamAgentRequest {
        agentId = requireText(agentId, "agentId");
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        instruction = requireText(instruction, "instruction");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timeoutMillis = Math.max(0, timeoutMillis);
    }

    public static TeamAgentRequest of(String agentId, String sessionId, String userId, String instruction) {
        return new TeamAgentRequest(agentId, sessionId, userId, instruction, Map.of(), 0);
    }

    public TeamAgentRequest withTimeoutMillis(long timeoutMillis) {
        return new TeamAgentRequest(agentId, sessionId, userId, instruction, metadata, timeoutMillis);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
