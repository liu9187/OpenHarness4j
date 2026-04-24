package io.openharness4j.api;

import java.util.List;

public record LLMResponse(
        Message message,
        List<ToolCall> toolCalls,
        Usage usage
) {
    public LLMResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.zero() : usage;
    }

    public static LLMResponse text(String content) {
        return new LLMResponse(Message.assistant(content), List.of(), Usage.zero());
    }

    public static LLMResponse toolCalls(String content, List<ToolCall> toolCalls) {
        return new LLMResponse(Message.assistant(content, toolCalls), toolCalls, Usage.zero());
    }

    public boolean hasToolCalls() {
        return !effectiveToolCalls().isEmpty();
    }

    public List<ToolCall> effectiveToolCalls() {
        if (!toolCalls.isEmpty()) {
            return toolCalls;
        }
        if (message != null && !message.toolCalls().isEmpty()) {
            return message.toolCalls();
        }
        return List.of();
    }
}
