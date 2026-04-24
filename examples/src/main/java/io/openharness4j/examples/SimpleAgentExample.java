package io.openharness4j.examples;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResult;
import io.openharness4j.llm.MockLLMAdapter;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;

import java.util.List;
import java.util.Map;

public class SimpleAgentExample {

    public static void main(String[] args) {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new EchoTool());

        MockLLMAdapter llmAdapter = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls(
                        "I will call echo.",
                        List.of(new ToolCall("call_echo_1", "echo", Map.of("text", "hello OpenHarness4j")))
                ),
                LLMResponse.text("Echo result received: hello OpenHarness4j")
        ));

        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                registry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                new AgentRuntimeConfig(8)
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "please echo hello"));

        System.out.println("traceId=" + response.traceId());
        System.out.println("finishReason=" + response.finishReason());
        System.out.println("content=" + response.content());
        System.out.println("toolCalls=" + response.toolCalls().size());
    }

    private static class EchoTool implements Tool {

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Return the provided text.";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    name(),
                    description(),
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "text", Map.of("type", "string", "description", "Text to echo")
                            ),
                            "required", List.of("text")
                    )
            );
        }

        @Override
        public ToolResult execute(ToolContext context) {
            Object text = context.args().get("text");
            if (!(text instanceof String value) || value.isBlank()) {
                return ToolResult.failed("INVALID_ARGS", "text must be a non-empty string");
            }
            return ToolResult.success(value, Map.of("text", value));
        }
    }
}
