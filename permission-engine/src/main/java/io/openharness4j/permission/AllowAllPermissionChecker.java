package io.openharness4j.permission;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.ToolCall;

public class AllowAllPermissionChecker implements PermissionChecker {

    @Override
    public PermissionDecision allow(ToolCall call, AgentContext context) {
        return PermissionDecision.allow();
    }
}
