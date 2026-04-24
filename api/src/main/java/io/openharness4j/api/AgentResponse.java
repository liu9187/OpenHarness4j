package io.openharness4j.api;

import java.util.List;

public record AgentResponse(
        String content,
        List<ToolCallRecord> toolCalls,
        Usage usage,
        String traceId,
        FinishReason finishReason
) {
    public AgentResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.zero() : usage;
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (finishReason == null) {
            throw new IllegalArgumentException("finishReason must not be null");
        }
    }
}
