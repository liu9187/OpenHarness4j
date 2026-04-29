package io.openharness4j.permission;

import io.openharness4j.api.RiskLevel;

public record ToolPermissionRule(
        String toolName,
        PermissionRuleAction action,
        RiskLevel riskLevel,
        String reason
) {
    public ToolPermissionRule {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
        reason = reason == null ? "" : reason;
    }

    public static ToolPermissionRule allow(String toolName) {
        return new ToolPermissionRule(toolName, PermissionRuleAction.ALLOW, RiskLevel.LOW, "allowed by policy");
    }

    public static ToolPermissionRule deny(String toolName, RiskLevel riskLevel, String reason) {
        return new ToolPermissionRule(toolName, PermissionRuleAction.DENY, riskLevel, reason);
    }
}
