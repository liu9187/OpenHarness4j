package io.openharness4j.permission;

import io.openharness4j.api.RiskLevel;

import java.util.regex.Pattern;

public record CommandPermissionRule(
        String pattern,
        CommandRuleMatchType matchType,
        PermissionRuleAction action,
        RiskLevel riskLevel,
        String reason
) {
    public CommandPermissionRule {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern must not be blank");
        }
        matchType = matchType == null ? CommandRuleMatchType.EXACT : matchType;
        action = action == null ? PermissionRuleAction.DENY : action;
        riskLevel = riskLevel == null ? RiskLevel.MEDIUM : riskLevel;
        reason = reason == null ? "" : reason;
    }

    public static CommandPermissionRule allowPrefix(String prefix) {
        return new CommandPermissionRule(prefix, CommandRuleMatchType.PREFIX, PermissionRuleAction.ALLOW, RiskLevel.LOW, "command allowed by policy");
    }

    public static CommandPermissionRule denyContains(String text, RiskLevel riskLevel, String reason) {
        return new CommandPermissionRule(text, CommandRuleMatchType.CONTAINS, PermissionRuleAction.DENY, riskLevel, reason);
    }

    boolean matches(String command) {
        return switch (matchType) {
            case EXACT -> command.equals(pattern);
            case PREFIX -> command.startsWith(pattern);
            case CONTAINS -> command.contains(pattern);
            case REGEX -> Pattern.compile(pattern).matcher(command).find();
        };
    }
}
