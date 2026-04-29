package io.openharness4j.permission;

@FunctionalInterface
public interface ToolApprovalHandler {

    ToolApprovalDecision requestApproval(ToolApprovalRequest request);
}
