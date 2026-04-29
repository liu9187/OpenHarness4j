package io.openharness4j.toolkit;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAndMcpToolTest {

    @Test
    void searchUsesConfiguredProvider() {
        SearchTool tool = new SearchTool(new InMemorySearchProvider(List.of(
                new SearchResult("OpenHarness4j", "https://example.test/openharness4j", "Java agent harness"),
                new SearchResult("Other", "https://example.test/other", "Unrelated")
        )));

        ToolResult result = tool.execute(context("search-call", Map.of("query", "agent", "limit", 3)));

        assertEquals(ToolResultStatus.SUCCESS, result.status());
        assertTrue(result.content().contains("OpenHarness4j"));
        assertEquals(1, result.data().get("count"));
    }

    @Test
    void mcpToolDelegatesToConfiguredClient() {
        McpClientTool tool = new McpClientTool(request -> ToolResult.success(
                "called " + request.server() + "/" + request.method(),
                Map.of("params", request.params())
        ));

        ToolResult result = tool.execute(context("mcp-call", Map.of(
                "server", "local",
                "method", "tools/list",
                "params", Map.of("cursor", "next")
        )));

        assertEquals(ToolResultStatus.SUCCESS, result.status());
        assertTrue(result.content().contains("local/tools/list"));
    }

    private static ToolContext context(String toolCallId, Map<String, Object> args) {
        return new ToolContext("session-1", "user-1", "trace-1", toolCallId, args, Map.of());
    }
}
