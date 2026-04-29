package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;

import java.util.LinkedHashMap;
import java.util.Map;

public class PolicyPermissionChecker implements PermissionChecker {

    private final PermissionPolicy policy;
    private final Map<String, ToolPermissionRule> rulesByTool = new LinkedHashMap<>();

    public PolicyPermissionChecker(PermissionPolicy policy) {
        this.policy = policy == null ? PermissionPolicy.allowByDefault(java.util.List.of()) : policy;
        for (ToolPermissionRule rule : this.policy.rules()) {
            rulesByTool.put(rule.toolName(), rule);
        }
    }

    @Override
    public PermissionDecision allow(ToolCall call, AgentContext context) {
        if (call == null) {
            return PermissionDecision.deny("tool call is null", RiskLevel.HIGH);
        }
        ToolPermissionRule rule = rulesByTool.get(call.name());
        if (rule != null) {
            if (rule.action() == PermissionRuleAction.ALLOW) {
                return PermissionDecision.allow();
            }
            String reason = rule.reason().isBlank()
                    ? "tool is denied by policy: " + call.name()
                    : rule.reason();
            return PermissionDecision.deny(reason, rule.riskLevel());
        }
        if (policy.defaultAllow()) {
            return PermissionDecision.allow();
        }
        return PermissionDecision.deny("tool is not explicitly allowed: " + call.name(), RiskLevel.MEDIUM);
    }
}
