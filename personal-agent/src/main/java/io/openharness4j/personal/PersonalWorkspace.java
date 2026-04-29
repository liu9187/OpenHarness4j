package io.openharness4j.personal;

import java.time.Instant;
import java.util.Map;

public record PersonalWorkspace(
        String workspaceId,
        String userId,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant lastActiveAt
) {
    public PersonalWorkspace {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        lastActiveAt = lastActiveAt == null ? createdAt : lastActiveAt;
    }

    public PersonalWorkspace touch() {
        return new PersonalWorkspace(workspaceId, userId, metadata, createdAt, Instant.now());
    }
}
