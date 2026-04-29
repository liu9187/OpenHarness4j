package io.openharness4j.task;

public record TaskSubmission(
        String taskId,
        TaskStatus status
) {
    public TaskSubmission {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
