package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;

public class AuditingPermissionChecker implements PermissionChecker {

    private final PermissionChecker delegate;
    private final PermissionAuditStore auditStore;

    public AuditingPermissionChecker(PermissionChecker delegate, PermissionAuditStore auditStore) {
        this.delegate = delegate == null ? (call, context) -> PermissionDecision.allow() : delegate;
        this.auditStore = auditStore == null ? new InMemoryPermissionAuditStore() : auditStore;
    }

    @Override
    public PermissionDecision allow(ToolCall call, AgentContext context) {
        PermissionDecision decision;
        try {
            decision = delegate.allow(call, context);
            if (decision == null) {
                decision = PermissionDecision.deny("permission checker returned null", RiskLevel.HIGH);
            }
        } catch (RuntimeException ex) {
            decision = PermissionDecision.deny("permission checker failed: " + safeMessage(ex), RiskLevel.HIGH);
        }
        auditStore.append(PermissionAuditEvent.from(call, context, decision));
        return decision;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
