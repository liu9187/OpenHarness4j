package io.openharness4j.tool;

import io.openharness4j.api.ToolDefinition;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {

    void register(Tool tool);

    Optional<Tool> get(String name);

    List<ToolDefinition> definitions();

    List<Tool> list();
}
