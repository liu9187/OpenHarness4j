package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.util.List;

public interface MemoryStore {

    List<Message> load(String sessionId);

    void save(String sessionId, Message message);

    void replace(String sessionId, List<Message> messages);

    void clear(String sessionId);

    static String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessionId;
    }
}
