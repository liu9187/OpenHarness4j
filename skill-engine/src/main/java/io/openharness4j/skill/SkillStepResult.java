package io.openharness4j.skill;

import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.Usage;

import java.util.List;

public record SkillStepResult(
        String name,
        SkillStepType type,
        SkillRunStatus status,
        String output,
        List<ToolCallRecord> toolCalls,
        Usage usage,
        String traceId,
        long durationMillis,
        String errorCode,
        String errorMessage
) {
    public SkillStepResult {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        output = output == null ? "" : output;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.zero() : usage;
        traceId = traceId == null ? "" : traceId;
        durationMillis = Math.max(0, durationMillis);
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
