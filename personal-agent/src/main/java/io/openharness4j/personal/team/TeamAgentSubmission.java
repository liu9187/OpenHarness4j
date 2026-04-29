package io.openharness4j.personal.team;

import io.openharness4j.task.TaskStatus;

public record TeamAgentSubmission(
        String taskId,
        String agentId,
        TaskStatus status
) {
    public TeamAgentSubmission {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
