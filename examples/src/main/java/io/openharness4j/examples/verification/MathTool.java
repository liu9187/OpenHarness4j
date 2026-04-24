package io.openharness4j.examples.verification;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class MathTool implements Tool {

    private final AtomicInteger executions = new AtomicInteger();

    @Override
    public String name() {
        return "add";
    }

    @Override
    public String description() {
        return "Add two numbers.";
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                name(),
                description(),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "left", Map.of("type", "number", "description", "Left operand"),
                                "right", Map.of("type", "number", "description", "Right operand")
                        ),
                        "required", List.of("left", "right")
                )
        );
    }

    @Override
    public ToolResult execute(ToolContext context) {
        executions.incrementAndGet();
        Number left = numberArg(context, "left");
        Number right = numberArg(context, "right");
        double result = left.doubleValue() + right.doubleValue();
        return ToolResult.success("sum=" + result, Map.of("sum", result));
    }

    int executions() {
        return executions.get();
    }

    private static Number numberArg(ToolContext context, String name) {
        Object value = context.args().get(name);
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException(name + " must be a number");
    }
}
