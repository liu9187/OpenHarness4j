package io.openharness4j.multiagent;

import io.openharness4j.api.FinishReason;
import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.Usage;

import java.util.List;

public record AgentTaskResult(
        String taskId,
        String agentId,
        String instruction,
        AgentTaskStatus status,
        String content,
        String traceId,
        FinishReason finishReason,
        List<ToolCallRecord> toolCalls,
        Usage usage,
        String errorCode,
        String errorMessage
) {
    public AgentTaskResult {
        taskId = requireText(taskId, "taskId");
        agentId = requireText(agentId, "agentId");
        instruction = instruction == null ? "" : instruction;
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        content = content == null ? "" : content;
        traceId = traceId == null ? "" : traceId;
        finishReason = finishReason == null ? FinishReason.ERROR : finishReason;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.zero() : usage;
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static AgentTaskResult failed(AgentTask task, String errorCode, String errorMessage) {
        return new AgentTaskResult(
                task.taskId(),
                task.agentId(),
                task.instruction(),
                AgentTaskStatus.FAILED,
                "",
                "",
                FinishReason.ERROR,
                List.of(),
                Usage.zero(),
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
