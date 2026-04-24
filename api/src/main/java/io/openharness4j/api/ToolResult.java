package io.openharness4j.api;

import java.util.Map;

public record ToolResult(
        ToolResultStatus status,
        String content,
        Map<String, Object> data,
        String errorCode,
        String errorMessage
) {
    public ToolResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        content = content == null ? "" : content;
        data = data == null ? Map.of() : Map.copyOf(data);
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static ToolResult success(String content) {
        return new ToolResult(ToolResultStatus.SUCCESS, content, Map.of(), "", "");
    }

    public static ToolResult success(String content, Map<String, Object> data) {
        return new ToolResult(ToolResultStatus.SUCCESS, content, data, "", "");
    }

    public static ToolResult failed(String errorCode, String errorMessage) {
        return new ToolResult(ToolResultStatus.FAILED, "", Map.of(), errorCode, errorMessage);
    }

    public static ToolResult permissionDenied(String reason) {
        return new ToolResult(ToolResultStatus.PERMISSION_DENIED, reason, Map.of(), "PERMISSION_DENIED", reason);
    }

    public Message toMessage(String toolCallId, String toolName) {
        String body = switch (status) {
            case SUCCESS -> content;
            case FAILED -> "Tool failed: " + errorCode + " - " + errorMessage;
            case PERMISSION_DENIED -> "Permission denied: " + errorMessage;
        };
        return Message.tool(toolCallId, toolName, body);
    }
}
