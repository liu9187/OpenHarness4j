package io.openharness4j.multiagent;

public record AgentConflict(
        String key,
        String firstAgentId,
        String secondAgentId,
        String firstValue,
        String secondValue,
        String reason
) {
    public AgentConflict {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        firstAgentId = firstAgentId == null ? "" : firstAgentId;
        secondAgentId = secondAgentId == null ? "" : secondAgentId;
        firstValue = firstValue == null ? "" : firstValue;
        secondValue = secondValue == null ? "" : secondValue;
        reason = reason == null ? "" : reason;
    }
}
