package io.openharness4j.toolkit;

import java.util.Map;

public record McpClientRequest(String server, String method, Map<String, Object> params) {
    public McpClientRequest {
        if (server == null || server.isBlank()) {
            throw new IllegalArgumentException("server must not be blank");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
