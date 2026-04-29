package io.openharness4j.permission;

import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;

import java.util.List;

public record CommandPermissionPolicy(boolean defaultAllow, List<CommandPermissionRule> rules) {

    public CommandPermissionPolicy {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static CommandPermissionPolicy allowByDefault(List<CommandPermissionRule> rules) {
        return new CommandPermissionPolicy(true, rules);
    }

    public static CommandPermissionPolicy denyByDefault(List<CommandPermissionRule> rules) {
        return new CommandPermissionPolicy(false, rules);
    }

    public PermissionDecision allow(String command) {
        if (command == null || command.isBlank()) {
            return PermissionDecision.deny("command is required", RiskLevel.HIGH);
        }
        for (CommandPermissionRule rule : rules) {
            if (rule.matches(command)) {
                if (rule.action() == PermissionRuleAction.ALLOW) {
                    return PermissionDecision.allow();
                }
                String reason = rule.reason().isBlank()
                        ? "command is denied by policy: " + command
                        : rule.reason();
                return PermissionDecision.deny(reason, rule.riskLevel());
            }
        }
        if (defaultAllow) {
            return PermissionDecision.allow();
        }
        return PermissionDecision.deny("command is not explicitly allowed: " + command, RiskLevel.MEDIUM);
    }
}
