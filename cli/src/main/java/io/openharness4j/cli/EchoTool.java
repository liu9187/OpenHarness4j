package io.openharness4j.cli;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

import java.util.List;
import java.util.Map;

final class EchoTool implements Tool {

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "Return the provided text.";
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of("text", Map.of("type", "string")),
                        "required", List.of("text")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context) {
        Object text = context.args().get("text");
        if (!(text instanceof String value)) {
            return ToolResult.failed("INVALID_ARGS", "text must be a string");
        }
        return ToolResult.success(value, Map.of("text", value));
    }
}
