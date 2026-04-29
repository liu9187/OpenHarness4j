package io.openharness4j.cli;

import io.openharness4j.skill.InMemorySkillRegistry;
import io.openharness4j.toolkit.McpClientTool;
import io.openharness4j.toolkit.ShellTool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CliDryRun {

    private CliDryRun() {
    }

    static CliDryRunReport evaluate(CliOptions options) {
        List<CliComponentStatus> components = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> nextActions = new ArrayList<>();
        boolean ready = true;

        if (options.hasMockProvider()) {
            components.add(ok("provider", "mock response provider configured"));
        } else if (options.hasConfiguredProvider()) {
            List<String> details = new ArrayList<>();
            details.add("profile=" + options.providerName);
            details.add("endpoint=" + options.providerEndpoint);
            details.add("model=" + modelSummary(options));
            components.add(ok("provider", details));
        } else {
            ready = false;
            components.add(error("provider", "missing provider; configure --mock-response or --provider-endpoint with --provider-model"));
            nextActions.add("Set --mock-response for local verification or configure --provider-endpoint and --provider-model.");
        }

        Set<String> enabledTools = normalizedSet(options.enabledTools);
        Set<String> selectedTools = normalizedSet(options.selectedTools);
        Set<String> deniedTools = normalizedSet(options.deniedTools);
        List<String> unsupportedTools = enabledTools.stream()
                .filter(tool -> !CliToolFactory.isSupported(tool))
                .toList();
        if (!unsupportedTools.isEmpty()) {
            ready = false;
            components.add(error("tools", "unsupported tools: " + unsupportedTools));
            nextActions.add("Remove unsupported tools or add them as application-level Tool implementations.");
        } else {
            components.add(ok("tools", enabledTools.isEmpty() ? "no built-in tools enabled" : "enabled=" + enabledTools));
        }

        for (String selectedTool : selectedTools) {
            if (!enabledTools.contains(selectedTool)) {
                ready = false;
                risks.add("selected tool is not enabled: " + selectedTool);
            }
            if (deniedTools.contains(selectedTool)) {
                ready = false;
                risks.add("selected tool is denied by policy: " + selectedTool);
            }
        }
        if (enabledTools.contains(ShellTool.NAME) && !deniedTools.contains(ShellTool.NAME)) {
            risks.add("shell tool is enabled; require command policy and approval before production use");
        }

        InMemorySkillRegistry skills = CliSkillLoader.load(options);
        List<String> skillIds = skills.list().stream().map(skill -> skill.id() + ":" + skill.version()).toList();
        if (options.skillId == null || options.skillId.isBlank()) {
            components.add(ok("skills", skillIds.isEmpty() ? "no skills loaded" : "loaded=" + skillIds));
        } else if (skills.get(options.skillId).isPresent()) {
            components.add(ok("skills", "selected skill=" + options.skillId));
        } else {
            ready = false;
            components.add(error("skills", "unknown skill: " + options.skillId));
            nextActions.add("Check --skill or add --skill-location for the requested skill.");
        }

        if (enabledTools.contains(McpClientTool.NAME)) {
            if (options.mcpServer == null || options.mcpServer.isBlank()) {
                risks.add("MCP tool is enabled but no --mcp-server was provided");
                components.add(warn("mcp", "MCP tool enabled without server configuration"));
            } else {
                components.add(ok("mcp", "server=" + options.mcpServer));
            }
        } else {
            components.add(ok("mcp", "MCP tool disabled"));
        }

        if (!risks.isEmpty()) {
            nextActions.add("Review dry-run risks before running the agent.");
        }
        boolean hasBlockingRisk = risks.stream().anyMatch(risk ->
                risk.contains("not enabled") || risk.contains("denied by policy")
        );
        return new CliDryRunReport(ready && !hasBlockingRisk, components, risks, nextActions);
    }

    static String formatPlain(CliDryRunReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("OpenHarness4j dry run\n");
        builder.append("ready: ").append(report.ready()).append('\n');
        for (CliComponentStatus component : report.components()) {
            builder.append("- ")
                    .append(component.name())
                    .append(": ")
                    .append(component.status())
                    .append(" ")
                    .append(component.details())
                    .append('\n');
        }
        if (!report.risks().isEmpty()) {
            builder.append("risks:\n");
            report.risks().forEach(risk -> builder.append("- ").append(risk).append('\n'));
        }
        if (!report.nextActions().isEmpty()) {
            builder.append("next actions:\n");
            report.nextActions().forEach(action -> builder.append("- ").append(action).append('\n'));
        }
        return builder.toString();
    }

    private static Set<String> normalizedSet(List<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .map(CliToolFactory::normalize)
                    .filter(value -> !value.isBlank())
                    .forEach(result::add);
        }
        return result;
    }

    private static String modelSummary(CliOptions options) {
        if (!options.providerModel.isBlank()) {
            return options.providerModel;
        }
        return "env:" + options.providerModelEnv;
    }

    private static CliComponentStatus ok(String name, String detail) {
        return ok(name, List.of(detail));
    }

    private static CliComponentStatus ok(String name, List<String> details) {
        return new CliComponentStatus(name, "OK", details);
    }

    private static CliComponentStatus warn(String name, String detail) {
        return new CliComponentStatus(name, "WARN", List.of(detail));
    }

    private static CliComponentStatus error(String name, String detail) {
        return new CliComponentStatus(name, "ERROR", List.of(detail));
    }
}
