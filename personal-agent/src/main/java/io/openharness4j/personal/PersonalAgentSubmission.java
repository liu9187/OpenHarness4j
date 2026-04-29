package io.openharness4j.personal;

import io.openharness4j.task.TaskStatus;

public record PersonalAgentSubmission(
        String taskId,
        TaskStatus status,
        String workspaceId,
        String conversationId,
        String channel
) {
    public PersonalAgentSubmission {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        channel = channel == null ? "" : channel;
    }
}
