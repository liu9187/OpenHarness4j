package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolResult;

import java.util.List;

public class CompositeToolExecutionHook implements ToolExecutionHook {

    private final List<ToolExecutionHook> hooks;

    public CompositeToolExecutionHook(List<ToolExecutionHook> hooks) {
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }

    @Override
    public PreToolUseResult beforeToolUse(ToolCall call, AgentContext context) {
        ToolCall current = call;
        for (ToolExecutionHook hook : hooks) {
            PreToolUseResult result = hook.beforeToolUse(current, context);
            if (result == null) {
                continue;
            }
            if (!result.allowed()) {
                return result;
            }
            current = result.toolCall();
        }
        return PreToolUseResult.allow(current);
    }

    @Override
    public void afterToolUse(ToolCall call, ToolResult result, AgentContext context, long durationMillis) {
        for (ToolExecutionHook hook : hooks) {
            hook.afterToolUse(call, result, context, durationMillis);
        }
    }
}
