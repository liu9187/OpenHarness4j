package io.openharness4j.tool;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResult;

public interface Tool {

    String name();

    String description();

    default ToolDefinition definition() {
        return new ToolDefinition(name(), description(), java.util.Map.of());
    }

    ToolResult execute(ToolContext context);
}
