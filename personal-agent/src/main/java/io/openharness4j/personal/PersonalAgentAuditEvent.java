package io.openharness4j.personal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PersonalAgentAuditEvent(
        String eventId,
        String actorUserId,
        String action,
        String resourceId,
        Map<String, Object> metadata,
        Instant occurredAt
) {
    public PersonalAgentAuditEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        actorUserId = actorUserId == null ? "" : actorUserId;
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        resourceId = resourceId == null ? "" : resourceId;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static PersonalAgentAuditEvent of(
            String actorUserId,
            String action,
            String resourceId,
            Map<String, Object> metadata
    ) {
        return new PersonalAgentAuditEvent(
                "audit_" + UUID.randomUUID(),
                actorUserId,
                action,
                resourceId,
                metadata,
                Instant.now()
        );
    }
}
