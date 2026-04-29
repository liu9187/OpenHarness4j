package io.openharness4j.multiagent;

import io.openharness4j.runtime.AgentRuntime;

public record SubAgentDefinition(
        String agentId,
        String role,
        AgentRuntime runtime
) {
    public SubAgentDefinition {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        role = role == null ? "" : role;
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
    }
}
