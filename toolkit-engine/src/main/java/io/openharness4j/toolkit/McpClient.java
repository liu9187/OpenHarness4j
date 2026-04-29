package io.openharness4j.toolkit;

import io.openharness4j.api.ToolResult;

@FunctionalInterface
public interface McpClient {

    ToolResult call(McpClientRequest request);
}
