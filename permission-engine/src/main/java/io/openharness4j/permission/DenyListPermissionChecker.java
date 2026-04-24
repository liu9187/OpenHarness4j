package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;

import java.util.Set;

public class DenyListPermissionChecker implements PermissionChecker {

    private final Set<String> deniedToolNames;

    public DenyListPermissionChecker(Set<String> deniedToolNames) {
        this.deniedToolNames = deniedToolNames == null ? Set.of() : Set.copyOf(deniedToolNames);
    }

    @Override
    public PermissionDecision allow(ToolCall call, AgentContext context) {
        if (call != null && deniedToolNames.contains(call.name())) {
            return PermissionDecision.deny("tool is denied by policy: " + call.name(), RiskLevel.HIGH);
        }
        return PermissionDecision.allow();
    }
}
