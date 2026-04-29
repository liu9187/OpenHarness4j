package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditingPermissionCheckerTest {

    @Test
    void appliesPolicyRulesAndAuditsDecision() {
        PermissionPolicy policy = PermissionPolicy.allowByDefault(List.of(
                ToolPermissionRule.deny("shell", RiskLevel.HIGH, "shell is blocked")
        ));
        InMemoryPermissionAuditStore store = new InMemoryPermissionAuditStore();
        AuditingPermissionChecker checker = new AuditingPermissionChecker(new PolicyPermissionChecker(policy), store);

        PermissionDecision decision = checker.allow(
                new ToolCall("call-shell", "shell", Map.of("command", "rm", "token", "secret")),
                context()
        );

        assertFalse(decision.allowed());
        assertEquals("shell is blocked", decision.reason());
        assertEquals(1, store.list().size());
        PermissionAuditEvent event = store.list().get(0);
        assertEquals("trace-1", event.traceId());
        assertEquals("shell", event.toolName());
        assertFalse(event.allowed());
        assertEquals(RiskLevel.HIGH, event.riskLevel());
        assertEquals("***", event.args().get("token"));
    }

    @Test
    void denyByDefaultRequiresExplicitAllow() {
        PolicyPermissionChecker checker = new PolicyPermissionChecker(PermissionPolicy.denyByDefault(List.of(
                ToolPermissionRule.allow("echo")
        )));

        assertTrue(checker.allow(new ToolCall("call-echo", "echo", Map.of()), context()).allowed());
        assertFalse(checker.allow(new ToolCall("call-http", "http", Map.of()), context()).allowed());
    }

    @Test
    void auditsDelegateFailureAsDeniedDecision() {
        InMemoryPermissionAuditStore store = new InMemoryPermissionAuditStore();
        AuditingPermissionChecker checker = new AuditingPermissionChecker((call, context) -> {
            throw new IllegalStateException("boom");
        }, store);

        PermissionDecision decision = checker.allow(new ToolCall("call", "tool", Map.of()), context());

        assertFalse(decision.allowed());
        assertEquals(1, store.list().size());
        assertEquals("permission checker failed: boom", store.list().get(0).reason());
    }

    private static AgentContext context() {
        return new AgentContext("session-1", "user-1", "trace-1", Map.of());
    }
}
