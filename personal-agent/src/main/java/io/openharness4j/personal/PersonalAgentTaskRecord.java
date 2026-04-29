package io.openharness4j.personal;

import java.time.Instant;
import java.util.Map;

public record PersonalAgentTaskRecord(
        String taskId,
        String userId,
        String workspaceId,
        String conversationId,
        String channel,
        Map<String, Object> metadata,
        Instant submittedAt
) {
    public PersonalAgentTaskRecord {
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
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        submittedAt = submittedAt == null ? Instant.now() : submittedAt;
    }
}
