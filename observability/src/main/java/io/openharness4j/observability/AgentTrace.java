package io.openharness4j.observability;

import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.api.Usage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AgentTrace {

    private final String traceId;
    private final Instant startedAt;
    private final List<ToolCallRecord> toolCalls = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private Usage usage = Usage.zero();

    public AgentTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        this.traceId = traceId;
        this.startedAt = Instant.now();
    }

    public String traceId() {
        return traceId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Usage usage() {
        return usage;
    }

    public List<ToolCallRecord> toolCalls() {
        return List.copyOf(toolCalls);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public void addUsage(Usage usage) {
        this.usage = this.usage.plus(usage);
    }

    public void recordToolResult(ToolCall call, ToolResult result, long durationMillis) {
        toolCalls.add(new ToolCallRecord(
                call.id(),
                call.name(),
                call.args(),
                result.status(),
                result.status() != ToolResultStatus.PERMISSION_DENIED,
                durationMillis,
                result.errorCode(),
                result.errorMessage()
        ));
    }

    public void recordPermissionDenied(ToolCall call, PermissionDecision decision, long durationMillis) {
        ToolResult result = ToolResult.permissionDenied(decision.reason());
        toolCalls.add(new ToolCallRecord(
                call.id(),
                call.name(),
                call.args(),
                result.status(),
                false,
                durationMillis,
                result.errorCode(),
                result.errorMessage()
        ));
    }

    public void recordMissingTool(ToolCall call, long durationMillis) {
        ToolResult result = ToolResult.failed("TOOL_NOT_FOUND", "tool not found: " + call.name());
        recordToolResult(call, result, durationMillis);
    }

    public void recordError(String error) {
        if (error != null && !error.isBlank()) {
            errors.add(error);
        }
    }
}
