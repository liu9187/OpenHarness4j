package io.openharness4j.api;

import java.util.Map;

public record ToolCallRecord(
        String toolCallId,
        String toolName,
        Map<String, Object> args,
        ToolResultStatus status,
        boolean allowed,
        long durationMillis,
        String errorCode,
        String errorMessage
) {
    public ToolCallRecord {
        toolCallId = toolCallId == null ? "" : toolCallId;
        toolName = toolName == null ? "" : toolName;
        args = args == null ? Map.of() : Map.copyOf(args);
        status = status == null ? ToolResultStatus.FAILED : status;
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
