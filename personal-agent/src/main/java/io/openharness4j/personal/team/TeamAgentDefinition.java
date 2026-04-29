package io.openharness4j.personal.team;

import io.openharness4j.runtime.AgentRuntime;

import java.util.Map;

public record TeamAgentDefinition(
        String agentId,
        String role,
        AgentRuntime runtime,
        Map<String, Object> metadata
) {
    public TeamAgentDefinition {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        role = role == null ? "" : role;
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public TeamAgentDefinition(String agentId, String role, AgentRuntime runtime) {
        this(agentId, role, runtime, Map.of());
    }
}
