package io.openharness4j.personal.team;

import io.openharness4j.task.TaskSnapshot;
import io.openharness4j.task.TaskStatus;

import java.time.Instant;
import java.util.Map;

public record TeamAgentSnapshot(
        String taskId,
        String agentId,
        String role,
        TaskStatus status,
        String content,
        Map<String, Object> data,
        String errorCode,
        String errorMessage,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> metadata
) {
    public TeamAgentSnapshot {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        role = role == null ? "" : role;
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
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    static TeamAgentSnapshot from(TaskSnapshot snapshot) {
        return new TeamAgentSnapshot(
                snapshot.taskId(),
                string(snapshot.metadata(), "agentId"),
                string(snapshot.metadata(), "role"),
                snapshot.status(),
                snapshot.content(),
                snapshot.data(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                snapshot.submittedAt(),
                snapshot.startedAt(),
                snapshot.completedAt(),
                snapshot.metadata()
        );
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
