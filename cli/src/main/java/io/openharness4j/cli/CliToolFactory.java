package io.openharness4j.cli;

import io.openharness4j.api.RiskLevel;
import io.openharness4j.permission.CommandPermissionPolicy;
import io.openharness4j.permission.CommandPermissionRule;
import io.openharness4j.permission.PathAccessMode;
import io.openharness4j.permission.PathAccessPolicy;
import io.openharness4j.permission.PathAccessRule;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.toolkit.FileTool;
import io.openharness4j.toolkit.InMemorySearchProvider;
import io.openharness4j.toolkit.McpClientTool;
import io.openharness4j.toolkit.SearchResult;
import io.openharness4j.toolkit.SearchTool;
import io.openharness4j.toolkit.ShellTool;
import io.openharness4j.toolkit.WebFetchTool;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

final class CliToolFactory {

    private CliToolFactory() {
    }

    static InMemoryToolRegistry create(CliOptions options) {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        for (String toolName : options.enabledTools.stream().map(CliToolFactory::normalize).distinct().toList()) {
            register(registry, toolName, options.baseDirectory);
        }
        return registry;
    }

    static String normalize(String toolName) {
        String value = toolName == null ? "" : toolName.trim().toLowerCase();
        return switch (value) {
            case "mcp" -> McpClientTool.NAME;
            case "web" -> WebFetchTool.NAME;
            default -> value;
        };
    }

    static boolean isSupported(String toolName) {
        return switch (normalize(toolName)) {
            case "echo", FileTool.NAME, ShellTool.NAME, WebFetchTool.NAME, SearchTool.NAME, McpClientTool.NAME -> true;
            default -> false;
        };
    }

    private static void register(InMemoryToolRegistry registry, String toolName, Path baseDirectory) {
        switch (toolName) {
            case "echo" -> registry.register(new EchoTool());
            case FileTool.NAME -> registry.register(new FileTool(
                    baseDirectory,
                    PathAccessPolicy.denyByDefault(List.of(PathAccessRule.allow(
                            baseDirectory,
                            EnumSet.allOf(PathAccessMode.class)
                    )))
            ));
            case ShellTool.NAME -> registry.register(new ShellTool(
                    baseDirectory,
                    CommandPermissionPolicy.denyByDefault(List.of(
                            CommandPermissionRule.denyContains("rm -rf", RiskLevel.HIGH, "destructive command denied")
                    ))
            ));
            case WebFetchTool.NAME -> registry.register(new WebFetchTool());
            case SearchTool.NAME -> registry.register(new SearchTool(new InMemorySearchProvider(List.of(
                    new SearchResult("OpenHarness4j CLI", "https://example.test/cli", "Local CLI dry-run result")
            ))));
            case McpClientTool.NAME -> registry.register(new McpClientTool(null));
            default -> throw new IllegalArgumentException("unsupported built-in tool: " + toolName);
        }
    }
}
