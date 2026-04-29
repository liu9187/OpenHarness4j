package io.openharness4j.permission;

import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.AgentContext;

import java.time.Instant;
import java.util.Map;

public record PermissionAuditEvent(
        Instant occurredAt,
        String traceId,
        String sessionId,
        String userId,
        String toolCallId,
        String toolName,
        Map<String, Object> args,
        boolean allowed,
        String reason,
        RiskLevel riskLevel
) {
    public PermissionAuditEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        traceId = traceId == null ? "" : traceId;
        sessionId = sessionId == null ? "" : sessionId;
        userId = userId == null ? "" : userId;
        toolCallId = toolCallId == null ? "" : toolCallId;
        toolName = toolName == null ? "" : toolName;
        args = args == null ? Map.of() : Map.copyOf(args);
        reason = reason == null ? "" : reason;
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
    }

    public static PermissionAuditEvent from(ToolCall call, AgentContext context, PermissionDecision decision) {
        return new PermissionAuditEvent(
                Instant.now(),
                context == null ? "" : context.traceId(),
                context == null ? "" : context.sessionId(),
                context == null ? "" : context.userId(),
                call == null ? "" : call.id(),
                call == null ? "" : call.name(),
                call == null ? Map.of() : sanitizeArgs(call.args()),
                decision != null && decision.allowed(),
                decision == null ? "permission decision is null" : decision.reason(),
                decision == null ? RiskLevel.HIGH : decision.riskLevel()
        );
    }

    private static Map<String, Object> sanitizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Map.of();
        }
        java.util.Map<String, Object> sanitized = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (sensitive(entry.getKey())) {
                sanitized.put(entry.getKey(), "***");
            } else {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized;
    }

    private static boolean sensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("authorization");
    }
}
