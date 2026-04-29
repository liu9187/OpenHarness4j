package io.openharness4j.permission;

import java.util.List;

public record PermissionPolicy(
        boolean defaultAllow,
        List<ToolPermissionRule> rules
) {
    public PermissionPolicy {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static PermissionPolicy allowByDefault(List<ToolPermissionRule> rules) {
        return new PermissionPolicy(true, rules);
    }

    public static PermissionPolicy denyByDefault(List<ToolPermissionRule> rules) {
        return new PermissionPolicy(false, rules);
    }
}
