package io.openharness4j.personal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PersonalAgentHistoryEntry(
        String entryId,
        String userId,
        String conversationId,
        String channel,
        String role,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public PersonalAgentHistoryEntry {
        if (entryId == null || entryId.isBlank()) {
            throw new IllegalArgumentException("entryId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        channel = channel == null ? "" : channel;
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static PersonalAgentHistoryEntry of(
            String userId,
            String conversationId,
            String channel,
            String role,
            String content,
            Map<String, Object> metadata
    ) {
        return new PersonalAgentHistoryEntry(
                "history_" + UUID.randomUUID(),
                userId,
                conversationId,
                channel,
                role,
                content,
                metadata,
                Instant.now()
        );
    }
}
