package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DenyListPermissionCheckerTest {

    @Test
    void deniesConfiguredTool() {
        DenyListPermissionChecker checker = new DenyListPermissionChecker(Set.of("shell"));

        PermissionDecision decision = checker.allow(
                ToolCall.of("shell", Map.of("command", "rm -rf /")),
                new AgentContext("s1", "u1", "t1", Map.of())
        );

        assertFalse(decision.allowed());
    }

    @Test
    void allowsUnlistedTool() {
        DenyListPermissionChecker checker = new DenyListPermissionChecker(Set.of("shell"));

        PermissionDecision decision = checker.allow(
                ToolCall.of("echo", Map.of("text", "hello")),
                new AgentContext("s1", "u1", "t1", Map.of())
        );

        assertTrue(decision.allowed());
    }
}
