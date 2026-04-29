package io.openharness4j.task;

import java.time.Instant;
import java.util.Map;

public record TaskContext(
        String taskId,
        String type,
        Map<String, Object> input,
        Map<String, Object> metadata,
        Instant deadline,
        TaskCancellationToken cancellationToken
) {
    public TaskContext {
        taskId = requireText(taskId, "taskId");
        type = requireText(type, "type");
        input = input == null ? Map.of() : Map.copyOf(input);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        cancellationToken = cancellationToken == null ? () -> false : cancellationToken;
    }

    public boolean cancellationRequested() {
        return cancellationToken.cancellationRequested() || Thread.currentThread().isInterrupted();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
