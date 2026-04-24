package io.openharness4j.api;

public record PermissionDecision(
        boolean allowed,
        String reason,
        RiskLevel riskLevel
) {
    public PermissionDecision {
        reason = reason == null ? "" : reason;
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
    }

    public static PermissionDecision allow() {
        return new PermissionDecision(true, "allowed", RiskLevel.LOW);
    }

    public static PermissionDecision deny(String reason, RiskLevel riskLevel) {
        return new PermissionDecision(false, reason, riskLevel);
    }
}
