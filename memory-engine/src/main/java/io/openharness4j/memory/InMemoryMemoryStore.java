package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMemoryStore implements MemoryStore {

    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    @Override
    public List<Message> load(String sessionId) {
        List<Message> messages = sessions.get(MemoryStore.normalizeSessionId(sessionId));
        return messages == null ? List.of() : List.copyOf(messages);
    }

    @Override
    public void save(String sessionId, Message message) {
        if (message == null) {
            return;
        }
        sessions.compute(MemoryStore.normalizeSessionId(sessionId), (key, existing) -> {
            List<Message> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.add(message);
            return List.copyOf(next);
        });
    }

    @Override
    public void replace(String sessionId, List<Message> messages) {
        sessions.put(MemoryStore.normalizeSessionId(sessionId), copy(messages));
    }

    @Override
    public void clear(String sessionId) {
        sessions.remove(MemoryStore.normalizeSessionId(sessionId));
    }

    private static List<Message> copy(List<Message> messages) {
        return messages == null ? List.of() : List.copyOf(messages);
    }
}
