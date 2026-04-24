package io.openharness4j.api;

import java.util.List;

public record Message(
        MessageRole role,
        String content,
        String name,
        String toolCallId,
        List<ToolCall> toolCalls
) {
    public Message {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        content = content == null ? "" : content;
        name = normalize(name);
        toolCallId = normalize(toolCallId);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message system(String content) {
        return new Message(MessageRole.SYSTEM, content, null, null, List.of());
    }

    public static Message user(String content) {
        return new Message(MessageRole.USER, content, null, null, List.of());
    }

    public static Message assistant(String content) {
        return new Message(MessageRole.ASSISTANT, content, null, null, List.of());
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(MessageRole.ASSISTANT, content, null, null, toolCalls);
    }

    public static Message tool(String toolCallId, String name, String content) {
        return new Message(MessageRole.TOOL, content, name, toolCallId, List.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
