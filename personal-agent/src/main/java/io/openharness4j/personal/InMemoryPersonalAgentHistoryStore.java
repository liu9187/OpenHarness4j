package io.openharness4j.personal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryPersonalAgentHistoryStore implements PersonalAgentHistoryStore {

    private final List<PersonalAgentHistoryEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(PersonalAgentHistoryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        entries.add(entry);
    }

    @Override
    public List<PersonalAgentHistoryEntry> listByConversation(String userId, String conversationId) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> userId.equals(entry.userId()))
                .filter(entry -> conversationId.equals(entry.conversationId()))
                .toList();
    }

    @Override
    public List<PersonalAgentHistoryEntry> listByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> userId.equals(entry.userId()))
                .toList();
    }
}
