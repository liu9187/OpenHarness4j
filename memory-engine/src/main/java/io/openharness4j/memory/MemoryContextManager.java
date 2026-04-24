package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;
import io.openharness4j.runtime.ContextManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MemoryContextManager implements ContextManager {

    private final MemoryStore memoryStore;
    private final MemoryWindowPolicy windowPolicy;

    public MemoryContextManager(MemoryStore memoryStore) {
        this(memoryStore, MemoryWindowPolicy.defaults());
    }

    public MemoryContextManager(MemoryStore memoryStore, MemoryWindowPolicy windowPolicy) {
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.windowPolicy = windowPolicy == null ? MemoryWindowPolicy.defaults() : windowPolicy;
    }

    @Override
    public List<Message> init(AgentRequest request) {
        List<Message> messages = new ArrayList<>(windowPolicy.apply(memoryStore.load(request.sessionId())));
        messages.add(Message.user(request.input()));
        return List.copyOf(messages);
    }

    @Override
    public void complete(AgentRequest request, List<Message> messages) {
        memoryStore.replace(request.sessionId(), windowPolicy.apply(messages));
    }
}
