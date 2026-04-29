package io.openharness4j.skill;

import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.Usage;

import java.util.List;

public record SkillRunResponse(
        String skillId,
        String skillVersion,
        SkillRunStatus status,
        String output,
        List<SkillStepResult> steps,
        List<ToolCallRecord> toolCalls,
        Usage usage,
        String traceId,
        String errorCode,
        String errorMessage
) {
    public SkillRunResponse {
        skillId = requireText(skillId, "skillId");
        skillVersion = skillVersion == null ? "" : skillVersion;
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        output = output == null ? "" : output;
        steps = steps == null ? List.of() : List.copyOf(steps);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.zero() : usage;
        traceId = requireText(traceId, "traceId");
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static SkillRunResponse failed(
            String skillId,
            String skillVersion,
            SkillRunStatus status,
            String traceId,
            String errorCode,
            String errorMessage
    ) {
        return new SkillRunResponse(
                skillId,
                skillVersion,
                status,
                "",
                List.of(),
                List.of(),
                Usage.zero(),
                traceId,
                errorCode,
                errorMessage
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
