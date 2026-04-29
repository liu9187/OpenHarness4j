package io.openharness4j.toolkit;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.permission.PathAccessMode;
import io.openharness4j.permission.PathAccessPolicy;
import io.openharness4j.permission.PathAccessRule;
import io.openharness4j.api.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAndWritesInsideAllowedBaseDirectory() {
        FileTool tool = new FileTool(tempDir, PathAccessPolicy.denyByDefault(List.of(
                PathAccessRule.allow(tempDir, Set.of(PathAccessMode.READ, PathAccessMode.WRITE, PathAccessMode.LIST))
        )));

        ToolResult write = tool.execute(context(Map.of("operation", "write", "path", "notes/demo.txt", "content", "hello")));
        ToolResult read = tool.execute(context(Map.of("operation", "read", "path", "notes/demo.txt")));
        ToolResult list = tool.execute(context(Map.of("operation", "list", "path", "notes")));

        assertEquals(ToolResultStatus.SUCCESS, write.status());
        assertEquals("hello", read.content());
        assertTrue(list.content().contains("demo.txt"));
    }

    @Test
    void deniesConfiguredPathAndRejectsEscapes() {
        FileTool tool = new FileTool(tempDir, PathAccessPolicy.denyByDefault(List.of(
                PathAccessRule.deny(tempDir.resolve("secret"), Set.of(PathAccessMode.READ), RiskLevel.HIGH, "secret denied"),
                PathAccessRule.allow(tempDir, Set.of(PathAccessMode.READ))
        )));

        ToolResult denied = tool.execute(context(Map.of("operation", "read", "path", "secret/data.txt")));
        ToolResult escaped = tool.execute(context(Map.of("operation", "read", "path", "../outside.txt")));

        assertEquals(ToolResultStatus.PERMISSION_DENIED, denied.status());
        assertEquals(ToolResultStatus.FAILED, escaped.status());
        assertEquals("INVALID_ARGS", escaped.errorCode());
    }

    private static ToolContext context(Map<String, Object> args) {
        return new ToolContext("session-1", "user-1", "trace-1", "call-1", args, Map.of());
    }
}
