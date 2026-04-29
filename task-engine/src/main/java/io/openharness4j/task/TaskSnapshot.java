package io.openharness4j.task;

import java.time.Instant;
import java.util.Map;

public record TaskSnapshot(
        String taskId,
        String type,
        TaskStatus status,
        String content,
        Map<String, Object> data,
        String errorCode,
        String errorMessage,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        long timeoutMillis,
        Map<String, Object> metadata
) {
    public TaskSnapshot {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        content = content == null ? "" : content;
        data = data == null ? Map.of() : Map.copyOf(data);
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
        if (submittedAt == null) {
            throw new IllegalArgumentException("submittedAt must not be null");
        }
        timeoutMillis = Math.max(0, timeoutMillis);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
