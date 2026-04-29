package io.openharness4j.toolkit;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.tool.Tool;

import java.util.Map;

public class McpClientTool implements Tool {

    public static final String NAME = "mcp_call";

    private final McpClient mcpClient;

    public McpClientTool(McpClient mcpClient) {
        this.mcpClient = mcpClient == null
                ? request -> ToolResult.failed("MCP_CLIENT_NOT_CONFIGURED", "MCP client is not configured")
                : mcpClient;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Call a configured MCP server through a pluggable McpClient.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            Object params = context.args().get("params");
            Map<String, Object> safeParams = params instanceof Map<?, ?> raw
                    ? copyStringKeyMap(raw)
                    : Map.of();
            return mcpClient.call(new McpClientRequest(
                    ToolArgs.requiredString(context.args(), "server"),
                    ToolArgs.requiredString(context.args(), "method"),
                    safeParams
            ));
        } catch (IllegalArgumentException ex) {
            return ToolResult.failed("INVALID_ARGS", safeMessage(ex));
        }
    }

    private static Map<String, Object> copyStringKeyMap(Map<?, ?> raw) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copy.put(key, entry.getValue());
            }
        }
        return Map.copyOf(copy);
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
