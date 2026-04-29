package io.openharness4j.runtime;

import io.openharness4j.api.AgentContext;
import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.Cost;
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
import io.openharness4j.permission.NoopToolExecutionHook;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.permission.PreToolUseResult;
import io.openharness4j.permission.ToolExecutionHook;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefaultAgentRuntime implements AgentRuntime {

    private final LLMAdapter llmAdapter;
    private final ToolRegistry toolRegistry;
    private final PermissionChecker permissionChecker;
    private final AgentTracer agentTracer;
    private final ContextManager contextManager;
    private final AgentRuntimeConfig config;
    private final ToolExecutionHook toolExecutionHook;

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
        this(
                llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                contextManager,
                config,
                new NoopToolExecutionHook()
        );
    }

    public DefaultAgentRuntime(
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            ContextManager contextManager,
            AgentRuntimeConfig config,
            ToolExecutionHook toolExecutionHook
    ) {
        this.llmAdapter = Objects.requireNonNull(llmAdapter, "llmAdapter must not be null");
        this.toolRegistry = toolRegistry == null ? new InMemoryToolRegistry() : toolRegistry;
        this.permissionChecker = permissionChecker == null ? new AllowAllPermissionChecker() : permissionChecker;
        this.agentTracer = agentTracer == null ? new DefaultAgentTracer() : agentTracer;
        this.contextManager = contextManager == null ? new DefaultContextManager() : contextManager;
        this.config = config == null ? AgentRuntimeConfig.defaults() : config;
        this.toolExecutionHook = toolExecutionHook == null ? new NoopToolExecutionHook() : toolExecutionHook;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        return run(request, AgentEventSink.noop());
    }

    @Override
    public AgentResponse run(AgentRequest request, AgentEventSink eventSink) {
        Objects.requireNonNull(request, "request must not be null");
        AgentEventSink sink = eventSink == null ? AgentEventSink.noop() : eventSink;

        AgentTrace trace = agentTracer.start(request);
        emit(sink, trace, AgentEvent.started(trace.traceId()));
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
                llmResponse = chatWithRetry(messages, trace, sink);
            } catch (RuntimeException ex) {
                trace.recordError(safeMessage(ex));
                emit(sink, trace, AgentEvent.error(trace.traceId(), "LLM call failed: " + safeMessage(ex)));
                return response(request, messages, "LLM call failed: " + safeMessage(ex), trace, FinishReason.ERROR, sink);
            }

            if (isEmpty(llmResponse)) {
                trace.recordError("LLM returned an empty response");
                emit(sink, trace, AgentEvent.error(trace.traceId(), "LLM returned an empty response"));
                return response(request, messages, "LLM returned an empty response.", trace, FinishReason.ERROR, sink);
            }

            trace.addUsage(llmResponse.usage());
            Cost cost = config.costEstimator().estimate(trace.usage());
            trace.recordCost(cost);
            emit(sink, trace, AgentEvent.llmResponse(trace.traceId(), llmResponse.usage()));
            emit(sink, trace, AgentEvent.costUpdated(trace.traceId(), trace.usage(), cost));

            if (llmResponse.message() != null) {
                messages.add(llmResponse.message());
                if (llmResponse.message().content() != null && !llmResponse.message().content().isBlank()) {
                    emit(sink, trace, AgentEvent.textDelta(trace.traceId(), llmResponse.message().content()));
                }
            }

            List<ToolCall> toolCalls = llmResponse.effectiveToolCalls();
            if (toolCalls.isEmpty()) {
                return response(request, messages, finalContent(llmResponse), trace, FinishReason.STOP, sink);
            }

            handleToolCalls(toolCalls, agentContext, request, trace, messages, sink);
        }

        return response(
                request,
                messages,
                "Agent stopped because max iteration limit was exceeded.",
                trace,
                FinishReason.MAX_ITERATION_EXCEEDED,
                sink
        );
    }

    private LLMResponse chatWithRetry(
            List<Message> messages,
            AgentTrace trace,
            AgentEventSink sink
    ) {
        RetryPolicy retryPolicy = config.llmRetryPolicy();
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            emit(sink, trace, AgentEvent.llmAttempt(trace.traceId(), attempt));
            try {
                return llmAdapter.chat(List.copyOf(messages), toolRegistry.definitions());
            } catch (RuntimeException ex) {
                lastError = ex;
                if (!retryPolicy.canRetryAfter(attempt)) {
                    throw ex;
                }
                emit(sink, trace, AgentEvent.llmRetry(trace.traceId(), attempt + 1, safeMessage(ex)));
                sleep(retryPolicy, trace);
            }
        }
        throw lastError == null ? new IllegalStateException("LLM call failed") : lastError;
    }

    private void handleToolCalls(
            List<ToolCall> toolCalls,
            AgentContext agentContext,
            AgentRequest request,
            AgentTrace trace,
            List<Message> messages,
            AgentEventSink sink
    ) {
        List<ToolExecutionOutcome> outcomes;
        if (config.parallelToolExecution() && toolCalls.size() > 1) {
            outcomes = executeToolCallsInParallel(toolCalls, agentContext, request, trace, sink);
        } else {
            outcomes = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                outcomes.add(executeToolCall(call, agentContext, request, trace, sink));
            }
        }

        for (ToolExecutionOutcome outcome : outcomes) {
            messages.add(outcome.result().toMessage(outcome.call().id(), outcome.call().name()));
            trace.recordToolResult(outcome.call(), outcome.result(), outcome.durationMillis());
        }
    }

    private List<ToolExecutionOutcome> executeToolCallsInParallel(
            List<ToolCall> toolCalls,
            AgentContext agentContext,
            AgentRequest request,
            AgentTrace trace,
            AgentEventSink sink
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(toolCalls.size());
        try {
            List<Future<ToolExecutionOutcome>> futures = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                Callable<ToolExecutionOutcome> task = () -> executeToolCall(call, agentContext, request, trace, sink);
                futures.add(executor.submit(task));
            }
            List<ToolExecutionOutcome> outcomes = new ArrayList<>();
            for (Future<ToolExecutionOutcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return toolCalls.stream()
                    .map(call -> new ToolExecutionOutcome(
                            call,
                            ToolResult.failed("TOOL_EXECUTION_INTERRUPTED", "parallel tool execution interrupted"),
                            0
                    ))
                    .toList();
        } catch (ExecutionException ex) {
            RuntimeException cause = ex.getCause() instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(ex.getCause());
            throw cause;
        } finally {
            executor.shutdownNow();
        }
    }

    private ToolExecutionOutcome executeToolCall(
            ToolCall call,
            AgentContext agentContext,
            AgentRequest request,
            AgentTrace trace,
            AgentEventSink sink
    ) {
        long startedAt = System.nanoTime();
        ToolCall effectiveCall = call;
        PreToolUseResult preToolUseResult;
        try {
            preToolUseResult = toolExecutionHook.beforeToolUse(call, agentContext);
        } catch (RuntimeException ex) {
            preToolUseResult = PreToolUseResult.deny("pre tool hook failed: " + safeMessage(ex), RiskLevel.HIGH);
        }

        if (preToolUseResult == null) {
            preToolUseResult = PreToolUseResult.allow(call);
        }

        if (!preToolUseResult.allowed()) {
            ToolResult result = ToolResult.permissionDenied(preToolUseResult.reason());
            afterToolUse(effectiveCall, result, agentContext, trace, elapsedMillis(startedAt));
            emit(sink, trace, AgentEvent.toolDone(trace.traceId(), effectiveCall, result));
            return new ToolExecutionOutcome(effectiveCall, result, elapsedMillis(startedAt));
        }

        effectiveCall = preToolUseResult.toolCall();
        emit(sink, trace, AgentEvent.toolStarted(trace.traceId(), effectiveCall));

        PermissionDecision decision;
        try {
            decision = permissionChecker.allow(effectiveCall, agentContext);
        } catch (RuntimeException ex) {
            decision = PermissionDecision.deny("permission checker failed: " + safeMessage(ex), RiskLevel.HIGH);
        }

        if (decision == null || !decision.allowed()) {
            PermissionDecision denied = decision == null
                    ? PermissionDecision.deny("permission checker returned null", RiskLevel.HIGH)
                    : decision;
            ToolResult result = ToolResult.permissionDenied(denied.reason());
            afterToolUse(effectiveCall, result, agentContext, trace, elapsedMillis(startedAt));
            emit(sink, trace, AgentEvent.toolDone(trace.traceId(), effectiveCall, result));
            return new ToolExecutionOutcome(effectiveCall, result, elapsedMillis(startedAt));
        }

        Optional<Tool> tool = toolRegistry.get(effectiveCall.name());
        if (tool.isEmpty()) {
            ToolResult result = ToolResult.failed("TOOL_NOT_FOUND", "tool not found: " + effectiveCall.name());
            afterToolUse(effectiveCall, result, agentContext, trace, elapsedMillis(startedAt));
            emit(sink, trace, AgentEvent.toolDone(trace.traceId(), effectiveCall, result));
            return new ToolExecutionOutcome(effectiveCall, result, elapsedMillis(startedAt));
        }

        ToolResult result = executeToolWithRetry(tool.get(), effectiveCall, request, trace, sink);
        afterToolUse(effectiveCall, result, agentContext, trace, elapsedMillis(startedAt));
        emit(sink, trace, AgentEvent.toolDone(trace.traceId(), effectiveCall, result));
        return new ToolExecutionOutcome(effectiveCall, result, elapsedMillis(startedAt));
    }

    private void afterToolUse(
            ToolCall call,
            ToolResult result,
            AgentContext agentContext,
            AgentTrace trace,
            long durationMillis
    ) {
        try {
            toolExecutionHook.afterToolUse(call, result, agentContext, durationMillis);
        } catch (RuntimeException ex) {
            trace.recordError("post tool hook failed: " + safeMessage(ex));
        }
    }

    private ToolResult executeToolWithRetry(
            Tool tool,
            ToolCall call,
            AgentRequest request,
            AgentTrace trace,
            AgentEventSink sink
    ) {
        RetryPolicy retryPolicy = config.toolRetryPolicy();
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            ToolContext toolContext = new ToolContext(
                    request.sessionId(),
                    request.userId(),
                    trace.traceId(),
                    call.id(),
                    call.args(),
                    request.metadata()
            );
            try {
                ToolResult result = tool.execute(toolContext);
                if (result == null) {
                    return ToolResult.failed("NULL_TOOL_RESULT", "tool returned null result");
                }
                return result;
            } catch (IllegalArgumentException ex) {
                return ToolResult.failed("INVALID_ARGS", safeMessage(ex));
            } catch (RuntimeException ex) {
                lastError = ex;
                if (!retryPolicy.canRetryAfter(attempt)) {
                    return ToolResult.failed("TOOL_EXECUTION_FAILED", safeMessage(ex));
                }
                emit(sink, trace, AgentEvent.toolRetry(trace.traceId(), call, attempt + 1, safeMessage(ex)));
                sleep(retryPolicy, trace);
            }
        }
        return ToolResult.failed(
                "TOOL_EXECUTION_FAILED",
                lastError == null ? "tool execution failed" : safeMessage(lastError)
        );
    }

    private AgentResponse response(
            AgentRequest request,
            List<Message> messages,
            String content,
            AgentTrace trace,
            FinishReason finishReason,
            AgentEventSink sink
    ) {
        try {
            contextManager.complete(request, List.copyOf(messages));
        } catch (RuntimeException ex) {
            trace.recordError("context completion failed: " + safeMessage(ex));
            agentTracer.finish(trace, FinishReason.ERROR);
            emit(sink, trace, AgentEvent.error(trace.traceId(), "context completion failed: " + safeMessage(ex)));
            emit(sink, trace, AgentEvent.done(trace.traceId(), FinishReason.ERROR, trace.usage(), trace.cost()));
            return new AgentResponse(
                    "Context completion failed: " + safeMessage(ex),
                    trace.toolCalls(),
                    trace.usage(),
                    trace.traceId(),
                    FinishReason.ERROR
                );
        }
        agentTracer.finish(trace, finishReason);
        emit(sink, trace, AgentEvent.done(trace.traceId(), finishReason, trace.usage(), trace.cost()));
        return new AgentResponse(content, trace.toolCalls(), trace.usage(), trace.traceId(), finishReason);
    }

    private static void emit(AgentEventSink sink, AgentTrace trace, AgentEvent event) {
        try {
            synchronized (sink) {
                sink.accept(event);
            }
        } catch (RuntimeException ex) {
            trace.recordError("event sink failed: " + safeMessage(ex));
        }
    }

    private static void sleep(RetryPolicy retryPolicy, AgentTrace trace) {
        if (retryPolicy.backoffMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(retryPolicy.backoffMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            trace.recordError("retry sleep interrupted");
        }
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

    private record ToolExecutionOutcome(ToolCall call, ToolResult result, long durationMillis) {
    }
}
