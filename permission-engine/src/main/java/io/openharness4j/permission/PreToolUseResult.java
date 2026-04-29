package io.openharness4j.permission;

import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;

public record PreToolUseResult(
        boolean allowed,
        ToolCall toolCall,
        String reason,
        RiskLevel riskLevel
) {
    public PreToolUseResult {
        reason = reason == null ? "" : reason;
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
        if (allowed && toolCall == null) {
            throw new IllegalArgumentException("toolCall must not be null when allowed");
        }
    }

    public static PreToolUseResult allow(ToolCall toolCall) {
        return new PreToolUseResult(true, toolCall, "allowed", RiskLevel.LOW);
    }

    public static PreToolUseResult deny(String reason, RiskLevel riskLevel) {
        return new PreToolUseResult(false, null, reason, riskLevel);
    }
}
