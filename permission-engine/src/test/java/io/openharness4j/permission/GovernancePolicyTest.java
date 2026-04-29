package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancePolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void pathPolicyAppliesOrderedRules() {
        PathAccessPolicy policy = PathAccessPolicy.denyByDefault(List.of(
                PathAccessRule.deny(tempDir.resolve("secret"), Set.of(PathAccessMode.READ), RiskLevel.HIGH, "secret blocked"),
                PathAccessRule.allow(tempDir, Set.of(PathAccessMode.READ, PathAccessMode.WRITE))
        ));

        assertTrue(policy.allow(tempDir.resolve("notes.txt"), PathAccessMode.READ).allowed());
        assertTrue(policy.allow(tempDir.resolve("notes.txt"), PathAccessMode.WRITE).allowed());
        assertFalse(policy.allow(tempDir.resolve("secret/passwords.txt"), PathAccessMode.READ).allowed());
        assertFalse(policy.allow(tempDir.resolve("notes.txt"), PathAccessMode.DELETE).allowed());
    }

    @Test
    void commandPolicySupportsDenyAndAllowPatterns() {
        CommandPermissionPolicy policy = CommandPermissionPolicy.denyByDefault(List.of(
                CommandPermissionRule.denyContains("rm -rf", RiskLevel.HIGH, "destructive command"),
                CommandPermissionRule.allowPrefix("printf ")
        ));

        assertTrue(policy.allow("printf hello").allowed());
        assertFalse(policy.allow("rm -rf /tmp/demo").allowed());
        assertFalse(policy.allow("echo nope").allowed());
    }

    @Test
    void approvalHookDelegatesDecisionToHandler() {
        AtomicReference<ToolApprovalRequest> captured = new AtomicReference<>();
        ApprovalRequiredToolHook hook = new ApprovalRequiredToolHook(
                Set.of("shell"),
                RiskLevel.HIGH,
                "shell requires approval",
                request -> {
                    captured.set(request);
                    return ToolApprovalDecision.deny("not approved");
                }
        );

        PreToolUseResult result = hook.beforeToolUse(
                new ToolCall("call-1", "shell", Map.of("command", "printf hello")),
                new AgentContext("session-1", "user-1", "trace-1", Map.of())
        );

        assertFalse(result.allowed());
        assertTrue(result.reason().contains("not approved"));
        assertTrue(captured.get().toolName().equals("shell"));
    }

    @Test
    void compositeHookAllowsMutationBeforeLaterHooks() {
        ToolExecutionHook rewrite = new ToolExecutionHook() {
            @Override
            public PreToolUseResult beforeToolUse(ToolCall call, AgentContext context) {
                return PreToolUseResult.allow(new ToolCall(call.id(), call.name(), Map.of("text", "rewritten")));
            }
        };
        ToolExecutionHook inspect = new ToolExecutionHook() {
            @Override
            public PreToolUseResult beforeToolUse(ToolCall call, AgentContext context) {
                PermissionDecision decision = "rewritten".equals(call.args().get("text"))
                        ? PermissionDecision.allow()
                        : PermissionDecision.deny("not rewritten", RiskLevel.HIGH);
                return decision.allowed()
                        ? PreToolUseResult.allow(call)
                        : PreToolUseResult.deny(decision.reason(), decision.riskLevel());
            }
        };

        PreToolUseResult result = new CompositeToolExecutionHook(List.of(rewrite, inspect))
                .beforeToolUse(new ToolCall("call-1", "echo", Map.of("text", "original")), null);

        assertTrue(result.allowed());
        assertTrue(result.toolCall().args().containsValue("rewritten"));
    }
}
