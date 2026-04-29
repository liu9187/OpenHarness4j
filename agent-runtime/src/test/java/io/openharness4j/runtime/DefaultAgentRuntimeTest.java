package io.openharness4j.runtime;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.api.Usage;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.MockLLMAdapter;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.permission.PreToolUseResult;
import io.openharness4j.permission.ToolExecutionHook;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAgentRuntimeTest {

    @Test
    void returnsTextWhenNoToolCallIsPresent() {
        AgentRuntime runtime = runtime(new MockLLMAdapter(List.of(LLMResponse.text("hello"))), new InMemoryToolRegistry());

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "hi"));

        assertEquals("hello", response.content());
        assertEquals(FinishReason.STOP, response.finishReason());
        assertTrue(response.toolCalls().isEmpty());
        assertNotNull(response.traceId());
    }

    @Test
    void executesSingleToolAndReturnsFinalText() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new EchoTool());
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling echo", List.of(new ToolCall("c1", "echo", Map.of("text", "hello")))),
                LLMResponse.text("tool said hello")
        ));
        AgentRuntime runtime = runtime(llmAdapter, registry);

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "echo hello"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals("tool said hello", response.content());
        assertEquals(1, response.toolCalls().size());
        assertEquals(ToolResultStatus.SUCCESS, response.toolCalls().get(0).status());
    }

    @Test
    void permissionDeniedDoesNotExecuteTool() {
        AtomicBoolean executed = new AtomicBoolean(false);
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new Tool() {
            @Override
            public String name() {
                return "danger";
            }

            @Override
            public String description() {
                return "danger tool";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                executed.set(true);
                return ToolResult.success("should not happen");
            }
        });
        PermissionChecker deny = (call, context) -> PermissionDecision.deny("blocked", RiskLevel.HIGH);
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling danger", List.of(new ToolCall("c1", "danger", Map.of()))),
                LLMResponse.text("I cannot do that.")
        ));
        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                registry,
                deny,
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "do danger"));

        assertFalse(executed.get());
        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(ToolResultStatus.PERMISSION_DENIED, response.toolCalls().get(0).status());
        assertFalse(response.toolCalls().get(0).allowed());
    }

    @Test
    void checksPermissionBeforeEachToolExecution() {
        List<String> events = new java.util.ArrayList<>();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new RecordingTool("first", events));
        registry.register(new RecordingTool("second", events));
        PermissionChecker checker = (call, context) -> {
            events.add("permission:" + call.name());
            return PermissionDecision.allow();
        };
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls(
                        "calling two tools",
                        List.of(
                                new ToolCall("c1", "first", Map.of()),
                                new ToolCall("c2", "second", Map.of())
                        )
                ),
                LLMResponse.text("done")
        ));
        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                registry,
                checker,
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "run both"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(List.of("permission:first", "tool:first", "permission:second", "tool:second"), events);
    }

    @Test
    void keepsContextMessagesInExecutionOrder() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new EchoTool());
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<Message>> secondTurnMessages = new AtomicReference<>();
        LLMAdapter llmAdapter = (messages, tools) -> {
            if (calls.incrementAndGet() == 1) {
                return LLMResponse.toolCalls("calling echo", List.of(new ToolCall("c1", "echo", Map.of("text", "hello"))));
            }
            secondTurnMessages.set(messages);
            return LLMResponse.text("done");
        };
        AgentRuntime runtime = runtime(llmAdapter, registry);

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "echo hello"));

        assertEquals(FinishReason.STOP, response.finishReason());
        List<Message> messages = secondTurnMessages.get();
        assertEquals(3, messages.size());
        assertEquals(MessageRole.USER, messages.get(0).role());
        assertEquals("echo hello", messages.get(0).content());
        assertEquals(MessageRole.ASSISTANT, messages.get(1).role());
        assertEquals("calling echo", messages.get(1).content());
        assertEquals(MessageRole.TOOL, messages.get(2).role());
        assertEquals("c1", messages.get(2).toolCallId());
        assertEquals("echo", messages.get(2).name());
        assertEquals("hello", messages.get(2).content());
    }

    @Test
    void recordsMissingToolAndLetsModelRecover() {
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling missing", List.of(new ToolCall("c1", "missing", Map.of()))),
                LLMResponse.text("The requested tool is unavailable.")
        ));
        AgentRuntime runtime = runtime(llmAdapter, new InMemoryToolRegistry());

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "call missing"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(ToolResultStatus.FAILED, response.toolCalls().get(0).status());
        assertEquals("TOOL_NOT_FOUND", response.toolCalls().get(0).errorCode());
    }

    @Test
    void returnsErrorWhenLlmCallThrowsException() {
        AgentRuntime runtime = runtime(
                (messages, tools) -> {
                    throw new IllegalStateException("timeout");
                },
                new InMemoryToolRegistry()
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "fail please"));

        assertEquals(FinishReason.ERROR, response.finishReason());
        assertTrue(response.content().contains("LLM call failed"));
        assertTrue(response.content().contains("timeout"));
    }

    @Test
    void returnsErrorWhenLlmResponseIsEmpty() {
        AgentRuntime runtime = runtime(
                (messages, tools) -> new LLMResponse(null, List.of(), Usage.zero()),
                new InMemoryToolRegistry()
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "empty please"));

        assertEquals(FinishReason.ERROR, response.finishReason());
        assertEquals("LLM returned an empty response.", response.content());
    }

    @Test
    void treatsMissingUsageAsZeroUsage() {
        AgentRuntime runtime = runtime(
                (messages, tools) -> new LLMResponse(Message.assistant("done"), List.of(), null),
                new InMemoryToolRegistry()
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "usage missing"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(0, response.usage().promptTokens());
        assertEquals(0, response.usage().completionTokens());
        assertEquals(0, response.usage().totalTokens());
    }

    @Test
    void convertsInvalidToolArgumentsToFailedToolResult() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new ThrowingArgTool());
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling invalid", List.of(new ToolCall("c1", "requires_number", Map.of("number", "not-a-number")))),
                LLMResponse.text("invalid args handled")
        ));
        AgentRuntime runtime = runtime(llmAdapter, registry);

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "call invalid"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(ToolResultStatus.FAILED, response.toolCalls().get(0).status());
        assertEquals("INVALID_ARGS", response.toolCalls().get(0).errorCode());
    }

    @Test
    void convertsToolExceptionToFailedToolResult() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new ExplodingTool());
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling exploding", List.of(new ToolCall("c1", "explode", Map.of()))),
                LLMResponse.text("tool error handled")
        ));
        AgentRuntime runtime = runtime(llmAdapter, registry);

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "explode"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(ToolResultStatus.FAILED, response.toolCalls().get(0).status());
        assertEquals("TOOL_EXECUTION_FAILED", response.toolCalls().get(0).errorCode());
    }

    @Test
    void aggregatesUsageAcrossLlmTurns() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new EchoTool());
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                new LLMResponse(
                        Message.assistant("calling echo"),
                        List.of(new ToolCall("c1", "echo", Map.of("text", "hello"))),
                        new Usage(10, 2, 12)
                ),
                new LLMResponse(
                        Message.assistant("done"),
                        List.of(),
                        new Usage(4, 5, 9)
                )
        ));
        AgentRuntime runtime = runtime(llmAdapter, registry);

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "echo hello"));

        assertEquals(14, response.usage().promptTokens());
        assertEquals(7, response.usage().completionTokens());
        assertEquals(21, response.usage().totalTokens());
    }

    @Test
    void emitsStreamingEventsAndTracksEstimatedCost() {
        List<AgentEvent> events = new CopyOnWriteArrayList<>();
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(new LLMResponse(
                Message.assistant("priced answer"),
                List.of(),
                new Usage(1_000_000, 500_000, 1_500_000)
        )));
        AgentRuntime runtime = runtime(
                llmAdapter,
                new InMemoryToolRegistry(),
                AgentRuntimeConfig.defaults().withCostEstimator(new TokenPricingCostEstimator(
                        "USD",
                        new BigDecimal("1.00"),
                        new BigDecimal("2.00")
                ))
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "price this"), events::add);

        assertEquals(FinishReason.STOP, response.finishReason());
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.STARTED));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.LLM_ATTEMPT));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.LLM_RESPONSE));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.TEXT_DELTA));
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.DONE));
        AgentEvent costEvent = events.stream()
                .filter(event -> event.type() == AgentEventType.COST_UPDATED)
                .findFirst()
                .orElseThrow();
        assertEquals("USD", costEvent.cost().currency());
        assertEquals(0, new BigDecimal("2.00").compareTo(costEvent.cost().amount()));
    }

    @Test
    void retriesLlmCallsWhenPolicyAllows() {
        AtomicInteger attempts = new AtomicInteger();
        List<AgentEvent> events = new CopyOnWriteArrayList<>();
        LLMAdapter llmAdapter = (messages, tools) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary outage");
            }
            return LLMResponse.text("recovered");
        };
        AgentRuntime runtime = runtime(
                llmAdapter,
                new InMemoryToolRegistry(),
                AgentRuntimeConfig.defaults().withLlmRetryPolicy(RetryPolicy.fixedDelay(2, 0))
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "retry llm"), events::add);

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals("recovered", response.content());
        assertEquals(2, attempts.get());
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.LLM_RETRY));
    }

    @Test
    void retriesToolExecutionWhenPolicyAllows() {
        AtomicInteger attempts = new AtomicInteger();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new Tool() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public String description() {
                return "fails once";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("temporary tool failure");
                }
                return ToolResult.success("ok");
            }
        });
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling flaky", List.of(new ToolCall("c1", "flaky", Map.of()))),
                LLMResponse.text("tool recovered")
        ));
        List<AgentEvent> events = new CopyOnWriteArrayList<>();
        AgentRuntime runtime = runtime(
                llmAdapter,
                registry,
                AgentRuntimeConfig.defaults().withToolRetryPolicy(RetryPolicy.fixedDelay(2, 0))
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "retry tool"), events::add);

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(2, attempts.get());
        assertEquals(ToolResultStatus.SUCCESS, response.toolCalls().get(0).status());
        assertTrue(events.stream().anyMatch(event -> event.type() == AgentEventType.TOOL_RETRY));
    }

    @Test
    void executesIndependentToolCallsInParallelWhenEnabled() {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new SlowTool("first", active, maxActive));
        registry.register(new SlowTool("second", active, maxActive));
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls(
                        "calling slow tools",
                        List.of(
                                new ToolCall("c1", "first", Map.of()),
                                new ToolCall("c2", "second", Map.of())
                        )
                ),
                LLMResponse.text("parallel done")
        ));
        AgentRuntime runtime = runtime(
                llmAdapter,
                registry,
                AgentRuntimeConfig.defaults().withParallelToolExecution(true)
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "run in parallel"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertTrue(maxActive.get() > 1);
        assertEquals("first", response.toolCalls().get(0).toolName());
        assertEquals("second", response.toolCalls().get(1).toolName());
    }

    @Test
    void preToolHookCanRewriteToolCallBeforeExecution() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new EchoTool());
        LLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls("calling echo", List.of(new ToolCall("c1", "echo", Map.of("text", "original")))),
                LLMResponse.text("done")
        ));
        ToolExecutionHook hook = new ToolExecutionHook() {
            @Override
            public PreToolUseResult beforeToolUse(ToolCall call, io.openharness4j.api.AgentContext context) {
                return PreToolUseResult.allow(new ToolCall(call.id(), call.name(), Map.of("text", "rewritten")));
            }
        };
        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                registry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults(),
                hook
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "rewrite tool"));

        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals("rewritten", response.toolCalls().get(0).args().get("text"));
    }

    @Test
    void preToolHookCanDenyAndPostHookObservesResult() {
        AtomicReference<ToolResult> observed = new AtomicReference<>();
        AtomicBoolean executed = new AtomicBoolean(false);
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new Tool() {
            @Override
            public String name() {
                return "danger";
            }

            @Override
            public String description() {
                return "should not execute";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                executed.set(true);
                return ToolResult.success("bad");
            }
        });
        ToolExecutionHook hook = new ToolExecutionHook() {
            @Override
            public PreToolUseResult beforeToolUse(ToolCall call, io.openharness4j.api.AgentContext context) {
                return PreToolUseResult.deny("blocked by pre hook", RiskLevel.HIGH);
            }

            @Override
            public void afterToolUse(ToolCall call, ToolResult result, io.openharness4j.api.AgentContext context, long durationMillis) {
                observed.set(result);
            }
        };
        AgentRuntime runtime = new DefaultAgentRuntime(
                new MockLLMAdapter(List.of(
                        LLMResponse.toolCalls("calling danger", List.of(new ToolCall("c1", "danger", Map.of()))),
                        LLMResponse.text("denied")
                )),
                registry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults(),
                hook
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "deny tool"));

        assertFalse(executed.get());
        assertEquals(ToolResultStatus.PERMISSION_DENIED, response.toolCalls().get(0).status());
        assertEquals(ToolResultStatus.PERMISSION_DENIED, observed.get().status());
    }

    @Test
    void usesProvidedTraceIdWhenPresentInMetadata() {
        AgentRuntime runtime = runtime(new MockLLMAdapter(List.of(LLMResponse.text("ok"))), new InMemoryToolRegistry());

        AgentResponse response = runtime.run(new AgentRequest("s1", "u1", "hi", Map.of(DefaultAgentTracer.TRACE_ID_METADATA_KEY, "trace-123")));

        assertEquals("trace-123", response.traceId());
    }

    @Test
    void stopsWhenMaxIterationsIsExceeded() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new EchoTool());
        LLMAdapter llmAdapter = (messages, tools) -> LLMResponse.toolCalls(
                "again",
                List.of(new ToolCall("c" + messages.size(), "echo", Map.of("text", "loop")))
        );
        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                registry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                new AgentRuntimeConfig(2)
        );

        AgentResponse response = runtime.run(AgentRequest.of("s1", "u1", "loop"));

        assertEquals(FinishReason.MAX_ITERATION_EXCEEDED, response.finishReason());
        assertEquals(2, response.toolCalls().size());
    }

    private static AgentRuntime runtime(LLMAdapter adapter, InMemoryToolRegistry registry) {
        return runtime(adapter, registry, AgentRuntimeConfig.defaults());
    }

    private static AgentRuntime runtime(LLMAdapter adapter, InMemoryToolRegistry registry, AgentRuntimeConfig config) {
        return new DefaultAgentRuntime(
                adapter,
                registry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                config
        );
    }

    private static class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "echo text";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            Object text = context.args().get("text");
            if (!(text instanceof String value)) {
                return ToolResult.failed("INVALID_ARGS", "text is required");
            }
            return ToolResult.success(value);
        }
    }

    private static class ThrowingArgTool implements Tool {
        @Override
        public String name() {
            return "requires_number";
        }

        @Override
        public String description() {
            return "requires a numeric argument";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            Object value = context.args().get("number");
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("number is required");
            }
            return ToolResult.success("ok");
        }
    }

    private static class ExplodingTool implements Tool {
        @Override
        public String name() {
            return "explode";
        }

        @Override
        public String description() {
            return "throws a runtime exception";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            throw new IllegalStateException("boom");
        }
    }

    private record RecordingTool(String name, List<String> events) implements Tool {
        @Override
        public String description() {
            return "records execution order";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            events.add("tool:" + name);
            return ToolResult.success(name + " ok");
        }
    }

    private record SlowTool(String name, AtomicInteger active, AtomicInteger maxActive) implements Tool {
        @Override
        public String description() {
            return "slow tool";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return ToolResult.failed("INTERRUPTED", "slow tool interrupted");
            } finally {
                active.decrementAndGet();
            }
            return ToolResult.success(name + " ok");
        }
    }
}
