package io.openharness4j.permission;

import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;

import java.nio.file.Path;
import java.util.List;

public record PathAccessPolicy(boolean defaultAllow, List<PathAccessRule> rules) {

    public PathAccessPolicy {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static PathAccessPolicy allowByDefault(List<PathAccessRule> rules) {
        return new PathAccessPolicy(true, rules);
    }

    public static PathAccessPolicy denyByDefault(List<PathAccessRule> rules) {
        return new PathAccessPolicy(false, rules);
    }

    public PermissionDecision allow(Path path, PathAccessMode mode) {
        if (path == null) {
            return PermissionDecision.deny("path is required", RiskLevel.HIGH);
        }
        Path normalized = path.toAbsolutePath().normalize();
        for (PathAccessRule rule : rules) {
            if (rule.matches(normalized, mode)) {
                if (rule.action() == PermissionRuleAction.ALLOW) {
                    return PermissionDecision.allow();
                }
                String reason = rule.reason().isBlank()
                        ? "path is denied by policy: " + normalized
                        : rule.reason();
                return PermissionDecision.deny(reason, rule.riskLevel());
            }
        }
        if (defaultAllow) {
            return PermissionDecision.allow();
        }
        return PermissionDecision.deny("path is not explicitly allowed: " + normalized, RiskLevel.MEDIUM);
    }
}
