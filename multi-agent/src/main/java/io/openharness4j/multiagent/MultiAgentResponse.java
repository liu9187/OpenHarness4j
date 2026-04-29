package io.openharness4j.multiagent;

import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.Usage;

import java.util.List;

public record MultiAgentResponse(
        MultiAgentStatus status,
        String output,
        List<AgentTask> tasks,
        List<AgentTaskResult> results,
        List<AgentConflict> conflicts,
        List<ToolCallRecord> toolCalls,
        Usage usage,
        String errorCode,
        String errorMessage
) {
    public MultiAgentResponse {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        output = output == null ? "" : output;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        results = results == null ? List.of() : List.copyOf(results);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.zero() : usage;
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static MultiAgentResponse failed(String errorCode, String errorMessage) {
        return new MultiAgentResponse(
                MultiAgentStatus.FAILED,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Usage.zero(),
                errorCode,
                errorMessage
        );
    }
}
