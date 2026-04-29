package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;

import java.util.Set;

public class ApprovalRequiredToolHook implements ToolExecutionHook {

    private final Set<String> toolNames;
    private final RiskLevel riskLevel;
    private final String reason;
    private final ToolApprovalHandler approvalHandler;

    public ApprovalRequiredToolHook(
            Set<String> toolNames,
            RiskLevel riskLevel,
            String reason,
            ToolApprovalHandler approvalHandler
    ) {
        this.toolNames = toolNames == null ? Set.of() : Set.copyOf(toolNames);
        this.riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
        this.reason = reason == null ? "approval required" : reason;
        this.approvalHandler = approvalHandler == null
                ? request -> ToolApprovalDecision.deny("approval handler is not configured")
                : approvalHandler;
    }

    @Override
    public PreToolUseResult beforeToolUse(ToolCall call, AgentContext context) {
        if (call == null || (!toolNames.isEmpty() && !toolNames.contains(call.name()))) {
            return PreToolUseResult.allow(call);
        }
        ToolApprovalDecision decision = approvalHandler.requestApproval(new ToolApprovalRequest(
                context.sessionId(),
                context.userId(),
                context.traceId(),
                call.name(),
                call.args(),
                riskLevel,
                reason
        ));
        if (decision != null && decision.approved()) {
            return PreToolUseResult.allow(call);
        }
        String denyReason = decision == null || decision.reason().isBlank()
                ? "approval denied"
                : decision.reason();
        return PreToolUseResult.deny(denyReason, riskLevel);
    }
}
