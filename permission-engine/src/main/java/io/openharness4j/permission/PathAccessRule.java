package io.openharness4j.permission;

import io.openharness4j.api.RiskLevel;

import java.nio.file.Path;
import java.util.Set;

public record PathAccessRule(
        Path path,
        Set<PathAccessMode> modes,
        PermissionRuleAction action,
        RiskLevel riskLevel,
        String reason
) {
    public PathAccessRule {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        modes = modes == null || modes.isEmpty() ? Set.of(PathAccessMode.values()) : Set.copyOf(modes);
        action = action == null ? PermissionRuleAction.DENY : action;
        riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
        reason = reason == null ? "" : reason;
        path = path.toAbsolutePath().normalize();
    }

    public static PathAccessRule allow(Path path, Set<PathAccessMode> modes) {
        return new PathAccessRule(path, modes, PermissionRuleAction.ALLOW, RiskLevel.LOW, "path allowed by policy");
    }

    public static PathAccessRule deny(Path path, Set<PathAccessMode> modes, RiskLevel riskLevel, String reason) {
        return new PathAccessRule(path, modes, PermissionRuleAction.DENY, riskLevel, reason);
    }

    boolean matches(Path candidate, PathAccessMode mode) {
        return modes.contains(mode) && candidate.toAbsolutePath().normalize().startsWith(path);
    }
}
