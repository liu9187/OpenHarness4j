package io.openharness4j.runtime;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.MockLLMAdapter;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
        return new DefaultAgentRuntime(
                adapter,
                registry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
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
}
