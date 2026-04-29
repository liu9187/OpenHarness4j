package io.openharness4j.multiagent;

import java.util.Map;
import java.util.UUID;

public record AgentTask(
        String taskId,
        String agentId,
        String instruction,
        Map<String, Object> metadata
) {
    public AgentTask {
        taskId = taskId == null || taskId.isBlank() ? "agent_task_" + UUID.randomUUID() : taskId;
        agentId = requireText(agentId, "agentId");
        instruction = requireText(instruction, "instruction");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentTask of(String agentId, String instruction) {
        return new AgentTask(null, agentId, instruction, Map.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
