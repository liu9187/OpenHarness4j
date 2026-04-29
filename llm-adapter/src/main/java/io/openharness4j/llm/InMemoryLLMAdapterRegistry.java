package io.openharness4j.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLLMAdapterRegistry implements LLMAdapterRegistry {

    private final Map<String, LLMAdapter> adapters = new ConcurrentHashMap<>();

    @Override
    public void register(String name, LLMAdapter adapter) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        LLMAdapter previous = adapters.putIfAbsent(name, adapter);
        if (previous != null) {
            throw new IllegalArgumentException("LLM adapter already registered: " + name);
        }
    }

    @Override
    public Optional<LLMAdapter> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(name));
    }

    @Override
    public List<String> names() {
        return new ArrayList<>(adapters.keySet());
    }
}
