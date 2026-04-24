package io.openharness4j.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultTest {

    @Test
    void convertsSuccessToToolMessage() {
        Message message = ToolResult.success("done").toMessage("call-1", "echo");

        assertEquals(MessageRole.TOOL, message.role());
        assertEquals("call-1", message.toolCallId());
        assertEquals("echo", message.name());
        assertEquals("done", message.content());
    }

    @Test
    void convertsFailureToToolMessage() {
        Message message = ToolResult.failed("INVALID_ARGS", "text is required").toMessage("call-1", "echo");

        assertEquals(MessageRole.TOOL, message.role());
        assertTrue(message.content().contains("INVALID_ARGS"));
        assertTrue(message.content().contains("text is required"));
    }

    @Test
    void convertsPermissionDeniedToToolMessage() {
        Message message = ToolResult.permissionDenied("blocked").toMessage("call-1", "shell");

        assertEquals(MessageRole.TOOL, message.role());
        assertTrue(message.content().contains("Permission denied"));
        assertTrue(message.content().contains("blocked"));
    }
}
