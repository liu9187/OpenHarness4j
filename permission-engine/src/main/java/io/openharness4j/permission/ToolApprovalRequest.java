package io.openharness4j.permission;

import io.openharness4j.api.RiskLevel;

import java.util.Map;

public record ToolApprovalRequest(
        String sessionId,
        String userId,
        String traceId,
        String toolName,
        Map<String, Object> args,
        RiskLevel riskLevel,
        String reason
) {
    public ToolApprovalRequest {
        sessionId = safe(sessionId);
        userId = safe(userId);
        traceId = safe(traceId);
        toolName = safe(toolName);
        args = args == null ? Map.of() : Map.copyOf(args);
        riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
        reason = safe(reason);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
