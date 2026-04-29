package io.openharness4j.task;

import java.util.Map;

public record TaskResult(
        String content,
        Map<String, Object> data,
        String errorCode,
        String errorMessage
) {
    public TaskResult {
        content = content == null ? "" : content;
        data = data == null ? Map.of() : Map.copyOf(data);
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static TaskResult success(String content) {
        return new TaskResult(content, Map.of(), "", "");
    }

    public static TaskResult success(String content, Map<String, Object> data) {
        return new TaskResult(content, data, "", "");
    }

    public static TaskResult failed(String errorCode, String errorMessage) {
        return new TaskResult("", Map.of(), errorCode, errorMessage);
    }

    public boolean success() {
        return errorCode.isBlank() && errorMessage.isBlank();
    }
}
