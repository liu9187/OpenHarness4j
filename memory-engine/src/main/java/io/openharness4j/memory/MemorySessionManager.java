package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.util.List;
import java.util.Objects;

public class MemorySessionManager {

    private final MemoryStore memoryStore;

    public MemorySessionManager(MemoryStore memoryStore) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
    }

    public List<Message> resume(String sessionId) {
        return memoryStore.load(sessionId);
    }

    public void replace(String sessionId, List<Message> messages) {
        memoryStore.replace(sessionId, messages);
    }

    public void append(String sessionId, Message message) {
        memoryStore.save(sessionId, message);
    }

    public void clear(String sessionId) {
        memoryStore.clear(sessionId);
    }
}
