package io.openharness4j.runtime;

import io.openharness4j.api.Cost;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.Usage;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(
        AgentEventType type,
        String traceId,
        Instant timestamp,
        String message,
        String toolCallId,
        String toolName,
        Usage usage,
        Cost cost,
        int attempt,
        Map<String, Object> metadata
) {
    public AgentEvent {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        timestamp = timestamp == null ? Instant.now() : timestamp;
        message = message == null ? "" : message;
        toolCallId = toolCallId == null ? "" : toolCallId;
        toolName = toolName == null ? "" : toolName;
        usage = usage == null ? Usage.zero() : usage;
        cost = cost == null ? Cost.zero() : cost;
        attempt = Math.max(0, attempt);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AgentEvent started(String traceId) {
        return simple(AgentEventType.STARTED, traceId, "runtime started");
    }

    public static AgentEvent llmAttempt(String traceId, int attempt) {
        return new AgentEvent(
                AgentEventType.LLM_ATTEMPT,
                traceId,
                Instant.now(),
                "llm attempt " + attempt,
                "",
                "",
                Usage.zero(),
                Cost.zero(),
                attempt,
                Map.of()
        );
    }

    public static AgentEvent llmRetry(String traceId, int attempt, String reason) {
        return new AgentEvent(
                AgentEventType.LLM_RETRY,
                traceId,
                Instant.now(),
                reason,
                "",
                "",
                Usage.zero(),
                Cost.zero(),
                attempt,
                Map.of()
        );
    }

    public static AgentEvent llmResponse(String traceId, Usage usage) {
        return new AgentEvent(
                AgentEventType.LLM_RESPONSE,
                traceId,
                Instant.now(),
                "llm response received",
                "",
                "",
                usage,
                Cost.zero(),
                0,
                Map.of()
        );
    }

    public static AgentEvent textDelta(String traceId, String content) {
        return simple(AgentEventType.TEXT_DELTA, traceId, content);
    }

    public static AgentEvent toolStarted(String traceId, ToolCall call) {
        return new AgentEvent(
                AgentEventType.TOOL_STARTED,
                traceId,
                Instant.now(),
                "tool started: " + call.name(),
                call.id(),
                call.name(),
                Usage.zero(),
                Cost.zero(),
                0,
                Map.of()
        );
    }

    public static AgentEvent toolRetry(String traceId, ToolCall call, int attempt, String reason) {
        return new AgentEvent(
                AgentEventType.TOOL_RETRY,
                traceId,
                Instant.now(),
                reason,
                call.id(),
                call.name(),
                Usage.zero(),
                Cost.zero(),
                attempt,
                Map.of()
        );
    }

    public static AgentEvent toolDone(String traceId, ToolCall call, ToolResult result) {
        return new AgentEvent(
                AgentEventType.TOOL_DONE,
                traceId,
                Instant.now(),
                "tool done: " + call.name(),
                call.id(),
                call.name(),
                Usage.zero(),
                Cost.zero(),
                0,
                Map.of(
                        "status", result.status().name(),
                        "allowed", result.status().name().equals("PERMISSION_DENIED") ? "false" : "true",
                        "errorCode", result.errorCode()
                )
        );
    }

    public static AgentEvent costUpdated(String traceId, Usage usage, Cost cost) {
        return new AgentEvent(
                AgentEventType.COST_UPDATED,
                traceId,
                Instant.now(),
                "cost updated",
                "",
                "",
                usage,
                cost,
                0,
                Map.of()
        );
    }

    public static AgentEvent done(String traceId, FinishReason finishReason, Usage usage, Cost cost) {
        return new AgentEvent(
                AgentEventType.DONE,
                traceId,
                Instant.now(),
                "runtime done",
                "",
                "",
                usage,
                cost,
                0,
                Map.of("finishReason", finishReason.name())
        );
    }

    public static AgentEvent error(String traceId, String message) {
        return simple(AgentEventType.ERROR, traceId, message);
    }

    private static AgentEvent simple(AgentEventType type, String traceId, String message) {
        return new AgentEvent(
                type,
                traceId,
                Instant.now(),
                message,
                "",
                "",
                Usage.zero(),
                Cost.zero(),
                0,
                Map.of()
        );
    }
}
