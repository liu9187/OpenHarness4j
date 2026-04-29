package io.openharness4j.personal;

import io.openharness4j.task.TaskSnapshot;
import io.openharness4j.task.TaskStatus;

import java.time.Instant;
import java.util.Map;

public record PersonalAgentTaskSnapshot(
        String taskId,
        String userId,
        String workspaceId,
        String conversationId,
        String channel,
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
    public PersonalAgentTaskSnapshot {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        channel = channel == null ? "" : channel;
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

    public static PersonalAgentTaskSnapshot from(TaskSnapshot snapshot, PersonalAgentTaskRecord record) {
        return new PersonalAgentTaskSnapshot(
                snapshot.taskId(),
                record.userId(),
                record.workspaceId(),
                record.conversationId(),
                record.channel(),
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
}
