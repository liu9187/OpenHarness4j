package io.openharness4j.personal;

import java.util.List;

public interface PersonalAgentAuditStore {

    void append(PersonalAgentAuditEvent event);

    List<PersonalAgentAuditEvent> list();

    default List<PersonalAgentAuditEvent> listByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return list().stream()
                .filter(event -> userId.equals(event.actorUserId()))
                .toList();
    }
}
