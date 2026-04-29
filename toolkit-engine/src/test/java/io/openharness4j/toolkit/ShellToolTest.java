package io.openharness4j.toolkit;

import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.permission.CommandPermissionPolicy;
import io.openharness4j.permission.CommandPermissionRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellToolTest {

    @TempDir
    Path tempDir;

    @Test
    void executesAllowedCommand() {
        ShellTool tool = new ShellTool(
                tempDir,
                CommandPermissionPolicy.denyByDefault(List.of(CommandPermissionRule.allowPrefix("printf ")))
        );

        ToolResult result = tool.execute(context(Map.of("command", "printf hello")));

        assertEquals(ToolResultStatus.SUCCESS, result.status());
        assertEquals("hello", result.content());
    }

    @Test
    void deniesDangerousCommandBeforeExecution() {
        ShellTool tool = new ShellTool(
                tempDir,
                CommandPermissionPolicy.denyByDefault(List.of(
                        CommandPermissionRule.denyContains("rm -rf", RiskLevel.HIGH, "destructive"),
                        CommandPermissionRule.allowPrefix("rm ")
                ))
        );

        ToolResult result = tool.execute(context(Map.of("command", "rm -rf /tmp/example")));

        assertEquals(ToolResultStatus.PERMISSION_DENIED, result.status());
        assertTrue(result.content().contains("destructive"));
    }

    private static ToolContext context(Map<String, Object> args) {
        return new ToolContext("session-1", "user-1", "trace-1", "call-1", args, Map.of());
    }
}
