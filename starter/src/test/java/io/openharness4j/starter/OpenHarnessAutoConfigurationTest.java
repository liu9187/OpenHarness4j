package io.openharness4j.starter;

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
import io.openharness4j.memory.MemoryContextManager;
import io.openharness4j.memory.MemoryStore;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenHarnessAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenHarnessAutoConfiguration.class));

    @Test
    void createsSupportBeansButNoRuntimeWithoutLlmAdapter() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("toolRegistry"));
            assertTrue(context.containsBean("permissionChecker"));
            assertTrue(context.containsBean("agentTracer"));
            assertTrue(context.containsBean("memoryStore"));
            assertTrue(context.getBean(ContextManager.class) instanceof MemoryContextManager);
            assertFalse(context.containsBean("agentRuntime"));
        });
    }

    @Test
    void canDisableMemoryContextManager() {
        contextRunner
                .withPropertyValues("openharness.memory.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("memoryStore"));
                    assertTrue(context.getBean(ContextManager.class) instanceof DefaultContextManager);
                });
    }

    @Test
    void createsAgentRuntimeWhenLlmAdapterBeanExists() {
        contextRunner
                .withUserConfiguration(TextOnlyLlmConfiguration.class)
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);

                    AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "hello"));

                    assertEquals(FinishReason.STOP, response.finishReason());
                    assertEquals("starter ok", response.content());
                    assertNotNull(response.traceId());
                });
    }

    @Test
    void starterMemoryRemembersSameSessionAcrossRuntimeCalls() {
        contextRunner
                .withUserConfiguration(MemoryLlmConfiguration.class)
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);
                    MemoryLlmConfiguration.CapturingLlmAdapter adapter = context.getBean(MemoryLlmConfiguration.CapturingLlmAdapter.class);

                    runtime.run(AgentRequest.of("session-memory", "user-1", "remember mango"));
                    AgentResponse second = runtime.run(AgentRequest.of("session-memory", "user-1", "what did I say?"));

                    assertEquals(FinishReason.STOP, second.finishReason());
                    assertTrue(adapter.secondRunMessages().stream().anyMatch(message -> message.content().equals("remember mango")));
                    assertTrue(adapter.secondRunMessages().stream().anyMatch(message -> message.content().equals("stored mango")));
                    assertEquals(4, context.getBean(MemoryStore.class).load("session-memory").size());
                });
    }

    @Test
    void registersToolBeansInDefaultToolRegistry() {
        contextRunner
                .withUserConfiguration(ToolBeanConfiguration.class)
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);
                    CountingTool tool = context.getBean(CountingTool.class);

                    AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "call echo"));

                    assertEquals(FinishReason.STOP, response.finishReason());
                    assertEquals(1, tool.executions());
                    assertEquals(ToolResultStatus.SUCCESS, response.toolCalls().get(0).status());
                });
    }

    @Test
    void bindsMaxIterationsProperty() {
        contextRunner
                .withUserConfiguration(LoopingLlmConfiguration.class)
                .withPropertyValues("openharness.agent.max-iterations=1")
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);

                    AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "loop"));

                    assertEquals(FinishReason.MAX_ITERATION_EXCEEDED, response.finishReason());
                    assertEquals(1, response.toolCalls().size());
                });
    }

    @Test
    void usesCustomToolRegistryAndPermissionCheckerBeans() {
        contextRunner
                .withUserConfiguration(CustomBeansConfiguration.class)
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);
                    CountingTool tool = context.getBean(CountingTool.class);

                    AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "call echo"));

                    assertEquals(FinishReason.STOP, response.finishReason());
                    assertEquals(0, tool.executions());
                    assertEquals(ToolResultStatus.PERMISSION_DENIED, response.toolCalls().get(0).status());
                });
    }

    static class TextOnlyLlmConfiguration {
        @Bean
        LLMAdapter llmAdapter() {
            return (messages, tools) -> LLMResponse.text("starter ok");
        }
    }

    static class LoopingLlmConfiguration {
        @Bean
        LLMAdapter llmAdapter() {
            return (messages, tools) -> LLMResponse.toolCalls(
                    "loop",
                    List.of(new ToolCall("call_" + messages.size(), "missing_tool", Map.of()))
            );
        }
    }

    static class ToolBeanConfiguration {
        @Bean
        CountingTool countingTool() {
            return new CountingTool();
        }

        @Bean
        LLMAdapter llmAdapter() {
            return new LLMAdapter() {
                private boolean called;

                @Override
                public LLMResponse chat(List<io.openharness4j.api.Message> messages, List<io.openharness4j.api.ToolDefinition> tools) {
                    if (!called) {
                        called = true;
                        return LLMResponse.toolCalls(
                                "calling echo",
                                List.of(new ToolCall("call_echo", "echo", Map.of("text", "hello")))
                        );
                    }
                    return LLMResponse.text("done");
                }
            };
        }
    }

    static class MemoryLlmConfiguration {
        @Bean
        CapturingLlmAdapter llmAdapter() {
            return new CapturingLlmAdapter();
        }

        static class CapturingLlmAdapter implements LLMAdapter {
            private final AtomicInteger calls = new AtomicInteger();
            private final AtomicReference<List<io.openharness4j.api.Message>> secondRunMessages = new AtomicReference<>();

            @Override
            public LLMResponse chat(List<io.openharness4j.api.Message> messages, List<io.openharness4j.api.ToolDefinition> tools) {
                if (calls.incrementAndGet() == 1) {
                    return LLMResponse.text("stored mango");
                }
                secondRunMessages.set(messages);
                return LLMResponse.text("mango");
            }

            List<io.openharness4j.api.Message> secondRunMessages() {
                return secondRunMessages.get();
            }
        }
    }

    static class CustomBeansConfiguration {
        @Bean
        CountingTool countingTool() {
            return new CountingTool();
        }

        @Bean
        ToolRegistry toolRegistry(CountingTool countingTool) {
            InMemoryToolRegistry registry = new InMemoryToolRegistry();
            registry.register(countingTool);
            return registry;
        }

        @Bean
        PermissionChecker permissionChecker() {
            return (call, context) -> PermissionDecision.deny("blocked by starter test", RiskLevel.HIGH);
        }

        @Bean
        LLMAdapter llmAdapter() {
            return new LLMAdapter() {
                private boolean called;

                @Override
                public LLMResponse chat(List<io.openharness4j.api.Message> messages, List<io.openharness4j.api.ToolDefinition> tools) {
                    if (!called) {
                        called = true;
                        return LLMResponse.toolCalls(
                                "calling echo",
                                List.of(new ToolCall("call_echo", "echo", Map.of("text", "secret")))
                        );
                    }
                    return LLMResponse.text("blocked");
                }
            };
        }
    }

    static class CountingTool implements Tool {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "count executions";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            executions.incrementAndGet();
            return ToolResult.success("ok");
        }

        int executions() {
            return executions.get();
        }
    }
}
