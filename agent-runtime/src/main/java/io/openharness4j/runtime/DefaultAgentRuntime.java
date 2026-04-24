package io.openharness4j.runtime;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.observability.AgentTrace;
import io.openharness4j.observability.AgentTracer;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultAgentRuntime implements AgentRuntime {

    private final LLMAdapter llmAdapter;
    private final ToolRegistry toolRegistry;
    private final PermissionChecker permissionChecker;
    private final AgentTracer agentTracer;
    private final ContextManager contextManager;
    private final AgentRuntimeConfig config;

    public DefaultAgentRuntime(LLMAdapter llmAdapter, ToolRegistry toolRegistry) {
        this(
                llmAdapter,
                toolRegistry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );
    }

    public DefaultAgentRuntime(
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            ContextManager contextManager,
            AgentRuntimeConfig config
    ) {
        this.llmAdapter = Objects.requireNonNull(llmAdapter, "llmAdapter must not be null");
        this.toolRegistry = toolRegistry == null ? new InMemoryToolRegistry() : toolRegistry;
        this.permissionChecker = permissionChecker == null ? new AllowAllPermissionChecker() : permissionChecker;
        this.agentTracer = agentTracer == null ? new DefaultAgentTracer() : agentTracer;
        this.contextManager = contextManager == null ? new DefaultContextManager() : contextManager;
        this.config = config == null ? AgentRuntimeConfig.defaults() : config;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        AgentTrace trace = agentTracer.start(request);
        AgentContext agentContext = new AgentContext(
                request.sessionId(),
                request.userId(),
                trace.traceId(),
                request.metadata()
        );
        List<Message> messages = new ArrayList<>(contextManager.init(request));

        for (int i = 0; i < config.maxIterations(); i++) {
            LLMResponse llmResponse;
            try {
                llmResponse = llmAdapter.chat(List.copyOf(messages), toolRegistry.definitions());
            } catch (RuntimeException ex) {
                trace.recordError(safeMessage(ex));
                return response("LLM call failed: " + safeMessage(ex), trace, FinishReason.ERROR);
            }

            if (isEmpty(llmResponse)) {
                trace.recordError("LLM returned an empty response");
                return response("LLM returned an empty response.", trace, FinishReason.ERROR);
            }

            trace.addUsage(llmResponse.usage());

            if (llmResponse.message() != null) {
                messages.add(llmResponse.message());
            }

            List<ToolCall> toolCalls = llmResponse.effectiveToolCalls();
            if (toolCalls.isEmpty()) {
                return response(finalContent(llmResponse), trace, FinishReason.STOP);
            }

            for (ToolCall call : toolCalls) {
                handleToolCall(call, agentContext, request, trace, messages);
            }
        }

        return response("Agent stopped because max iteration limit was exceeded.", trace, FinishReason.MAX_ITERATION_EXCEEDED);
    }

    private void handleToolCall(
            ToolCall call,
            AgentContext agentContext,
            AgentRequest request,
            AgentTrace trace,
            List<Message> messages
    ) {
        long startedAt = System.nanoTime();

        PermissionDecision decision;
        try {
            decision = permissionChecker.allow(call, agentContext);
        } catch (RuntimeException ex) {
            decision = PermissionDecision.deny("permission checker failed: " + safeMessage(ex), RiskLevel.HIGH);
        }

        if (decision == null || !decision.allowed()) {
            PermissionDecision denied = decision == null
                    ? PermissionDecision.deny("permission checker returned null", RiskLevel.HIGH)
                    : decision;
            ToolResult result = ToolResult.permissionDenied(denied.reason());
            messages.add(result.toMessage(call.id(), call.name()));
            trace.recordPermissionDenied(call, denied, elapsedMillis(startedAt));
            return;
        }

        Optional<Tool> tool = toolRegistry.get(call.name());
        if (tool.isEmpty()) {
            ToolResult result = ToolResult.failed("TOOL_NOT_FOUND", "tool not found: " + call.name());
            messages.add(result.toMessage(call.id(), call.name()));
            trace.recordMissingTool(call, elapsedMillis(startedAt));
            return;
        }

        ToolResult result;
        try {
            ToolContext toolContext = new ToolContext(
                    request.sessionId(),
                    request.userId(),
                    trace.traceId(),
                    call.id(),
                    call.args(),
                    request.metadata()
            );
            result = tool.get().execute(toolContext);
            if (result == null) {
                result = ToolResult.failed("NULL_TOOL_RESULT", "tool returned null result");
            }
        } catch (IllegalArgumentException ex) {
            result = ToolResult.failed("INVALID_ARGS", safeMessage(ex));
        } catch (RuntimeException ex) {
            result = ToolResult.failed("TOOL_EXECUTION_FAILED", safeMessage(ex));
        }

        messages.add(result.toMessage(call.id(), call.name()));
        trace.recordToolResult(call, result, elapsedMillis(startedAt));
    }

    private static AgentResponse response(String content, AgentTrace trace, FinishReason finishReason) {
        return new AgentResponse(content, trace.toolCalls(), trace.usage(), trace.traceId(), finishReason);
    }

    private static boolean isEmpty(LLMResponse response) {
        return response == null || (response.message() == null && response.effectiveToolCalls().isEmpty());
    }

    private static String finalContent(LLMResponse response) {
        if (response.message() == null) {
            return "";
        }
        return response.message().content();
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
