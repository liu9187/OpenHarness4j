package io.openharness4j.examples.verification;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.api.Usage;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.MockLLMAdapter;
import io.openharness4j.memory.InMemoryMemoryStore;
import io.openharness4j.memory.MemoryContextManager;
import io.openharness4j.memory.MemoryWindowPolicy;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class OpenHarnessFeatureVerifier {

    private OpenHarnessFeatureVerifier() {
    }

    public static List<VerificationResult> runAll() {
        List<VerificationResult> results = new ArrayList<>();
        results.add(verifiesTextOnlyResponse());
        results.add(verifiesSingleToolCall());
        results.add(verifiesMultipleToolCalls());
        results.add(verifiesPermissionDenied());
        results.add(verifiesMissingToolRecovery());
        results.add(verifiesInvalidToolArguments());
        results.add(verifiesToolExecutionFailure());
        results.add(verifiesEmptyLlmResponse());
        results.add(verifiesUsageAggregation());
        results.add(verifiesMaxIterationGuard());
        results.add(verifiesCrossRequestMemory());
        return List.copyOf(results);
    }

    private static VerificationResult verifiesTextOnlyResponse() {
        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(LLMResponse.text("plain answer"))),
                new InMemoryToolRegistry(),
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-text", "user-1", "hello"));

        expect(response.finishReason() == FinishReason.STOP, "text-only response should stop");
        expect(response.content().equals("plain answer"), "text-only content mismatch");
        expect(response.toolCalls().isEmpty(), "text-only response should not record tool calls");
        expect(!response.traceId().isBlank(), "traceId should be present");
        return result("text-only response", response, "LLM answer returned without tool execution");
    }

    private static VerificationResult verifiesSingleToolCall() {
        EchoTool echoTool = new EchoTool();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(echoTool);

        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls(
                                "calling echo",
                                List.of(new ToolCall("call_echo", "echo", Map.of("text", "hello")))
                        ),
                        LLMResponse.text("echo completed")
                )),
                registry,
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-single-tool", "user-1", "echo hello"));

        expect(response.finishReason() == FinishReason.STOP, "single tool call should stop after final answer");
        expect(echoTool.executions() == 1, "echo tool should execute once");
        expect(response.toolCalls().size() == 1, "single tool call should record one call");
        expect(response.toolCalls().get(0).status() == ToolResultStatus.SUCCESS, "echo tool should succeed");
        return result("single tool call", response, "tool result is written back before final answer");
    }

    private static VerificationResult verifiesMultipleToolCalls() {
        EchoTool echoTool = new EchoTool();
        MathTool mathTool = new MathTool();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(echoTool);
        registry.register(mathTool);

        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls(
                                "calling two tools",
                                List.of(
                                        new ToolCall("call_echo", "echo", Map.of("text", "multi")),
                                        new ToolCall("call_add", "add", Map.of("left", 2, "right", 3))
                                )
                        ),
                        LLMResponse.text("multi tool completed")
                )),
                registry,
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-multi-tool", "user-1", "echo and add"));

        expect(response.finishReason() == FinishReason.STOP, "multi tool response should stop after final answer");
        expect(echoTool.executions() == 1, "echo should execute once");
        expect(mathTool.executions() == 1, "math should execute once");
        expect(response.toolCalls().size() == 2, "multi tool response should record two calls");
        expect(response.toolCalls().get(0).toolName().equals("echo"), "tools should execute in model order");
        expect(response.toolCalls().get(1).toolName().equals("add"), "tools should execute in model order");
        return result("multiple tool calls", response, "two tools execute sequentially in one LLM turn");
    }

    private static VerificationResult verifiesPermissionDenied() {
        EchoTool echoTool = new EchoTool();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(echoTool);
        PermissionChecker denyEcho = (call, context) -> PermissionDecision.deny("blocked in example", RiskLevel.HIGH);

        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls(
                                "calling denied tool",
                                List.of(new ToolCall("call_echo", "echo", Map.of("text", "secret")))
                        ),
                        LLMResponse.text("permission denied handled")
                )),
                registry,
                denyEcho,
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-permission", "user-1", "echo secret"));

        expect(response.finishReason() == FinishReason.STOP, "permission denial should still allow model recovery");
        expect(echoTool.executions() == 0, "denied tool must not execute");
        expect(response.toolCalls().size() == 1, "denied call should be recorded");
        expect(response.toolCalls().get(0).status() == ToolResultStatus.PERMISSION_DENIED, "call should be marked denied");
        expect(!response.toolCalls().get(0).allowed(), "call record should show denied");
        return result("permission denied", response, "permission hook blocks execution and records denial");
    }

    private static VerificationResult verifiesMissingToolRecovery() {
        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls(
                                "calling missing tool",
                                List.of(new ToolCall("call_missing", "missing_tool", Map.of()))
                        ),
                        LLMResponse.text("missing tool handled")
                )),
                new InMemoryToolRegistry(),
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-missing-tool", "user-1", "call missing"));

        expect(response.finishReason() == FinishReason.STOP, "missing tool should let model recover");
        expect(response.toolCalls().size() == 1, "missing tool should be recorded");
        expect(response.toolCalls().get(0).status() == ToolResultStatus.FAILED, "missing tool should be failed");
        expect(response.toolCalls().get(0).errorCode().equals("TOOL_NOT_FOUND"), "missing tool error code mismatch");
        return result("missing tool recovery", response, "TOOL_NOT_FOUND is written back as a tool result");
    }

    private static VerificationResult verifiesInvalidToolArguments() {
        MathTool mathTool = new MathTool();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(mathTool);

        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls(
                                "calling add with bad args",
                                List.of(new ToolCall("call_bad_add", "add", Map.of("left", "bad", "right", 3)))
                        ),
                        LLMResponse.text("invalid args handled")
                )),
                registry,
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-invalid-args", "user-1", "bad add"));

        expect(response.finishReason() == FinishReason.STOP, "invalid args should let model recover");
        expect(mathTool.executions() == 1, "math tool should be invoked once");
        expect(response.toolCalls().get(0).status() == ToolResultStatus.FAILED, "invalid args should fail tool call");
        expect(response.toolCalls().get(0).errorCode().equals("INVALID_ARGS"), "invalid args error code mismatch");
        return result("invalid tool args", response, "IllegalArgumentException becomes INVALID_ARGS");
    }

    private static VerificationResult verifiesToolExecutionFailure() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new ExplodingTool());

        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls(
                                "calling exploding tool",
                                List.of(new ToolCall("call_explode", "explode", Map.of()))
                        ),
                        LLMResponse.text("tool exception handled")
                )),
                registry,
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-tool-error", "user-1", "explode"));

        expect(response.finishReason() == FinishReason.STOP, "tool exception should let model recover");
        expect(response.toolCalls().get(0).status() == ToolResultStatus.FAILED, "tool exception should fail tool call");
        expect(response.toolCalls().get(0).errorCode().equals("TOOL_EXECUTION_FAILED"), "tool exception error code mismatch");
        return result("tool execution failure", response, "RuntimeException becomes TOOL_EXECUTION_FAILED");
    }

    private static VerificationResult verifiesEmptyLlmResponse() {
        AgentRuntime runtime = runtime(
                (messages, tools) -> new LLMResponse(null, List.of(), Usage.zero()),
                new InMemoryToolRegistry(),
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-empty-llm", "user-1", "empty response"));

        expect(response.finishReason() == FinishReason.ERROR, "empty LLM response should return ERROR");
        expect(response.toolCalls().isEmpty(), "empty LLM response should not record tool calls");
        return result("empty LLM response", response, "empty model output returns deterministic ERROR");
    }

    private static VerificationResult verifiesUsageAggregation() {
        EchoTool echoTool = new EchoTool();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(echoTool);

        AgentRuntime runtime = runtime(
                new MockLLMAdapter(List.of(
                        new LLMResponse(
                                Message.assistant("calling echo"),
                                List.of(new ToolCall("call_usage_echo", "echo", Map.of("text", "usage"))),
                                new Usage(10, 2, 12)
                        ),
                        new LLMResponse(
                                Message.assistant("usage done"),
                                List.of(),
                                new Usage(4, 5, 9)
                        )
                )),
                registry,
                new AllowAllPermissionChecker(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-usage", "user-1", "track usage"));

        expect(response.finishReason() == FinishReason.STOP, "usage scenario should stop");
        expect(response.usage().promptTokens() == 14, "prompt tokens should aggregate");
        expect(response.usage().completionTokens() == 7, "completion tokens should aggregate");
        expect(response.usage().totalTokens() == 21, "total tokens should aggregate");
        return result("usage aggregation", response, "usage is summed across LLM turns");
    }

    private static VerificationResult verifiesMaxIterationGuard() {
        EchoTool echoTool = new EchoTool();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(echoTool);
        LLMAdapter loopingAdapter = (messages, tools) -> LLMResponse.toolCalls(
                "looping",
                List.of(new ToolCall("call_" + messages.size(), "echo", Map.of("text", "loop")))
        );

        AgentRuntime runtime = runtime(
                loopingAdapter,
                registry,
                new AllowAllPermissionChecker(),
                new AgentRuntimeConfig(2)
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-loop", "user-1", "loop forever"));

        expect(response.finishReason() == FinishReason.MAX_ITERATION_EXCEEDED, "loop guard should stop execution");
        expect(echoTool.executions() == 2, "tool should execute once per allowed iteration");
        expect(response.toolCalls().size() == 2, "loop guard should record allowed iterations");
        return result("max iteration guard", response, "runtime stops after configured max iterations");
    }

    private static VerificationResult verifiesCrossRequestMemory() {
        InMemoryMemoryStore memoryStore = new InMemoryMemoryStore();
        MemoryContextManager contextManager = new MemoryContextManager(memoryStore, MemoryWindowPolicy.tailOnly(10));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<Message>> secondRunMessages = new AtomicReference<>();
        LLMAdapter llmAdapter = (messages, tools) -> {
            if (calls.incrementAndGet() == 1) {
                return LLMResponse.text("stored blueberry");
            }
            secondRunMessages.set(messages);
            return LLMResponse.text("memory loaded");
        };
        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                new InMemoryToolRegistry(),
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                contextManager,
                AgentRuntimeConfig.defaults()
        );

        runtime.run(AgentRequest.of("session-memory", "user-1", "remember blueberry"));
        AgentResponse response = runtime.run(AgentRequest.of("session-memory", "user-1", "what did I say?"));

        expect(response.finishReason() == FinishReason.STOP, "memory scenario should stop");
        expect(secondRunMessages.get().stream().anyMatch(message -> message.content().equals("remember blueberry")), "previous user message should be loaded");
        expect(secondRunMessages.get().stream().anyMatch(message -> message.content().equals("stored blueberry")), "previous assistant message should be loaded");
        expect(memoryStore.load("session-memory").size() == 4, "memory store should contain two request-response pairs");
        return result("cross-request memory", response, "same session loads previous user and assistant messages");
    }

    private static AgentRuntime runtime(
            LLMAdapter llmAdapter,
            ToolRegistry registry,
            PermissionChecker permissionChecker,
            AgentRuntimeConfig config
    ) {
        return new DefaultAgentRuntime(
                llmAdapter,
                registry,
                permissionChecker,
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                config
        );
    }

    private static VerificationResult result(String name, AgentResponse response, String detail) {
        return new VerificationResult(
                name,
                response.finishReason(),
                response.toolCalls().size(),
                response.usage().totalTokens(),
                response.traceId(),
                detail
        );
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
