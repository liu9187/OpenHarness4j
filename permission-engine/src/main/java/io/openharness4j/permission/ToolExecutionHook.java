package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolResult;

public interface ToolExecutionHook {

    default PreToolUseResult beforeToolUse(ToolCall call, AgentContext context) {
        return PreToolUseResult.allow(call);
    }

    default void afterToolUse(ToolCall call, ToolResult result, AgentContext context, long durationMillis) {
    }
}
