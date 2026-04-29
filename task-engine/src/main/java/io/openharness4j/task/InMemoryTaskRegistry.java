package io.openharness4j.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTaskRegistry implements TaskRegistry {

    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(TaskHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        TaskHandler previous = handlers.putIfAbsent(handler.type(), handler);
        if (previous != null) {
            throw new IllegalArgumentException("task handler already registered: " + handler.type());
        }
    }

    @Override
    public Optional<TaskHandler> get(String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(type));
    }

    @Override
    public List<TaskHandler> list() {
        return new ArrayList<>(handlers.values());
    }
}
