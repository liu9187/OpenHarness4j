package io.openharness4j.tool;

import io.openharness4j.api.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryToolRegistry implements ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    @Override
    public void register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
        Tool previous = tools.putIfAbsent(tool.name(), tool);
        if (previous != null) {
            throw new IllegalArgumentException("tool already registered: " + tool.name());
        }
    }

    @Override
    public Optional<Tool> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public List<ToolDefinition> definitions() {
        return tools.values()
                .stream()
                .map(Tool::definition)
                .toList();
    }

    @Override
    public List<Tool> list() {
        return new ArrayList<>(tools.values());
    }
}
