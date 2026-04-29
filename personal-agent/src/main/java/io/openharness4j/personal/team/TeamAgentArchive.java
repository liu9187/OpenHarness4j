package io.openharness4j.personal.team;

import java.time.Instant;

public record TeamAgentArchive(
        String archiveId,
        TeamAgentSnapshot snapshot,
        Instant archivedAt
) {
    public TeamAgentArchive {
        if (archiveId == null || archiveId.isBlank()) {
            throw new IllegalArgumentException("archiveId must not be blank");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        archivedAt = archivedAt == null ? Instant.now() : archivedAt;
    }
}
