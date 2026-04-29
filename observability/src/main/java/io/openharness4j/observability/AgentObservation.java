package io.openharness4j.observability;

import io.openharness4j.api.Cost;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.Usage;

import java.time.Instant;
import java.util.List;

public record AgentObservation(
        String traceId,
        Instant startedAt,
        Instant finishedAt,
        FinishReason finishReason,
        Usage usage,
        Cost cost,
        List<ToolCallRecord> toolCalls,
        List<String> errors
) {
    public AgentObservation {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        finishedAt = finishedAt == null ? Instant.now() : finishedAt;
        finishReason = finishReason == null ? FinishReason.ERROR : finishReason;
        usage = usage == null ? Usage.zero() : usage;
        cost = cost == null ? Cost.zero() : cost;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static AgentObservation from(AgentTrace trace, FinishReason finishReason) {
        return new AgentObservation(
                trace.traceId(),
                trace.startedAt(),
                Instant.now(),
                finishReason,
                trace.usage(),
                trace.cost(),
                trace.toolCalls(),
                trace.errors()
        );
    }
}
