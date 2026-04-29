package io.openharness4j.permission;

public record ToolApprovalDecision(boolean approved, String reason) {

    public ToolApprovalDecision {
        reason = reason == null ? "" : reason;
    }

    public static ToolApprovalDecision approve(String reason) {
        return new ToolApprovalDecision(true, reason);
    }

    public static ToolApprovalDecision deny(String reason) {
        return new ToolApprovalDecision(false, reason);
    }
}
