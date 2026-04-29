package io.openharness4j.personal;

import java.util.List;
import java.util.Optional;

public interface PersonalAgentService {

    PersonalAgentSubmission submit(PersonalAgentMessage message);

    Optional<PersonalAgentTaskSnapshot> get(String taskId);

    boolean cancel(String taskId);

    PersonalWorkspace workspace(String userId);

    List<PersonalAgentHistoryEntry> history(String userId, String conversationId);

    List<PersonalAgentAuditEvent> auditEvents();
}
