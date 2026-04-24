package io.openharness4j.examples.verification;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class EchoTool implements Tool {

    private final AtomicInteger executions = new AtomicInteger();

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
                        "properties", Map.of(
                                "text", Map.of("type", "string", "description", "Text to echo")
                        ),
                        "required", List.of("text")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context) {
        executions.incrementAndGet();
        Object text = context.args().get("text");
        if (!(text instanceof String value) || value.isBlank()) {
            return ToolResult.failed("INVALID_ARGS", "text must be a non-empty string");
        }
        return ToolResult.success(value, Map.of("text", value));
    }

    int executions() {
        return executions.get();
    }
}
