package io.openharness4j.personal;

import java.util.List;

public interface PersonalAgentHistoryStore {

    void append(PersonalAgentHistoryEntry entry);

    List<PersonalAgentHistoryEntry> listByConversation(String userId, String conversationId);

    List<PersonalAgentHistoryEntry> listByUser(String userId);
}
