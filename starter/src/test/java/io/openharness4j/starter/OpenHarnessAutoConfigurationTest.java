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
import io.openharness4j.llm.LLMAdapterRegistry;
import io.openharness4j.llm.NamedLLMAdapter;
import io.openharness4j.memory.MemoryContextManager;
import io.openharness4j.memory.MemorySessionManager;
import io.openharness4j.memory.MemoryStore;
import io.openharness4j.multiagent.AgentTask;
import io.openharness4j.multiagent.MultiAgentRequest;
import io.openharness4j.multiagent.MultiAgentResponse;
import io.openharness4j.multiagent.MultiAgentRuntime;
import io.openharness4j.multiagent.MultiAgentStatus;
import io.openharness4j.multiagent.PlanningAgent;
import io.openharness4j.multiagent.SubAgentDefinition;
import io.openharness4j.multiagent.SubAgentRegistry;
import io.openharness4j.observability.InMemoryObservationExporter;
import io.openharness4j.observability.ObservationExporter;
import io.openharness4j.permission.PermissionAuditStore;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.plugin.OpenHarnessPlugin;
import io.openharness4j.plugin.PluginContext;
import io.openharness4j.plugin.PluginDescriptor;
import io.openharness4j.plugin.PluginRegistry;
import io.openharness4j.plugin.PluginStatus;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.skill.SkillDefinition;
import io.openharness4j.skill.SkillExecutor;
import io.openharness4j.skill.SkillRegistry;
import io.openharness4j.skill.SkillRunRequest;
import io.openharness4j.skill.SkillRunResponse;
import io.openharness4j.skill.SkillRunStatus;
import io.openharness4j.task.TaskEngine;
import io.openharness4j.task.TaskHandler;
import io.openharness4j.task.TaskRegistry;
import io.openharness4j.task.TaskRequest;
import io.openharness4j.task.TaskResult;
import io.openharness4j.task.TaskSnapshot;
import io.openharness4j.task.TaskStatus;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
            assertTrue(context.containsBean("permissionAuditStore"));
            assertTrue(context.containsBean("observationExporter"));
            assertTrue(context.containsBean("llmAdapterRegistry"));
            assertTrue(context.containsBean("memoryStore"));
            assertTrue(context.containsBean("memorySessionManager"));
            assertTrue(context.getBean(ContextManager.class) instanceof MemoryContextManager);
            assertTrue(context.containsBean("skillRegistry"));
            assertTrue(context.containsBean("taskRegistry"));
            assertTrue(context.containsBean("taskEngine"));
            assertTrue(context.containsBean("subAgentRegistry"));
            assertTrue(context.containsBean("multiAgentRuntime"));
            assertTrue(context.containsBean("pluginRegistry"));
            assertTrue(context.containsBean("pluginManager"));
            assertFalse(context.containsBean("agentRuntime"));
            assertFalse(context.containsBean("skillExecutor"));
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
    void canDisableTaskEngine() {
        contextRunner
                .withPropertyValues("openharness.task.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("taskRegistry"));
                    assertFalse(context.containsBean("taskEngine"));
                });
    }

    @Test
    void canDisableMultiAgentRuntime() {
        contextRunner
                .withPropertyValues("openharness.multi-agent.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("subAgentRegistry"));
                    assertFalse(context.containsBean("multiAgentRuntime"));
                });
    }

    @Test
    void createsAgentRuntimeWhenLlmAdapterBeanExists() {
        contextRunner
                .withUserConfiguration(TextOnlyLlmConfiguration.class)
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);
                    ObservationExporter exporter = context.getBean(ObservationExporter.class);

                    AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "hello"));

                    assertEquals(FinishReason.STOP, response.finishReason());
                    assertEquals("starter ok", response.content());
                    assertNotNull(response.traceId());
                    assertEquals(1, ((InMemoryObservationExporter) exporter).list().size());
                });
    }

    @Test
    void defaultPermissionPolicyAuditsDeniedToolCalls() {
        contextRunner
                .withUserConfiguration(ToolBeanConfiguration.class)
                .withPropertyValues("openharness.permission.denied-tools[0]=echo")
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);
                    PermissionAuditStore auditStore = context.getBean(PermissionAuditStore.class);
                    CountingTool tool = context.getBean(CountingTool.class);

                    AgentResponse response = runtime.run(AgentRequest.of("session-1", "user-1", "call echo"));

                    assertEquals(FinishReason.STOP, response.finishReason());
                    assertEquals(0, tool.executions());
                    assertEquals(ToolResultStatus.PERMISSION_DENIED, response.toolCalls().get(0).status());
                    assertEquals(1, auditStore.list().size());
                    assertFalse(auditStore.list().get(0).allowed());
                    assertEquals("echo", auditStore.list().get(0).toolName());
                });
    }

    @Test
    void registersNamedLlmAdapters() {
        contextRunner
                .withUserConfiguration(NamedLlmConfiguration.class)
                .run(context -> {
                    LLMAdapterRegistry registry = context.getBean(LLMAdapterRegistry.class);

                    assertTrue(registry.get("primary").isPresent());
                });
    }

    @Test
    void activatesPluginBeansAndRegistersContributions() {
        contextRunner
                .withUserConfiguration(PluginConfiguration.class)
                .run(context -> {
                    PluginRegistry pluginRegistry = context.getBean(PluginRegistry.class);
                    ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

                    assertEquals(PluginStatus.ACTIVE, pluginRegistry.get("starter-plugin").orElseThrow().status());
                    assertTrue(toolRegistry.get("plugin_tool").isPresent());
                });
    }

    @Test
    void registersTaskHandlerBeansAndCreatesTaskEngine() {
        contextRunner
                .withUserConfiguration(TaskBeanConfiguration.class)
                .run(context -> {
                    TaskRegistry registry = context.getBean(TaskRegistry.class);
                    TaskEngine engine = context.getBean(TaskEngine.class);

                    assertTrue(registry.get("starter_task").isPresent());

                    String taskId = engine.submit(TaskRequest.of("starter_task", Map.of("name", "daily"))).taskId();
                    TaskSnapshot snapshot = awaitTaskStatus(engine, taskId, TaskStatus.SUCCEEDED);

                    assertEquals("task daily completed", snapshot.content());
                });
    }

    @Test
    void registersSubAgentBeansAndCreatesMultiAgentRuntime() {
        contextRunner
                .withUserConfiguration(MultiAgentConfiguration.class)
                .run(context -> {
                    SubAgentRegistry registry = context.getBean(SubAgentRegistry.class);
                    MultiAgentRuntime runtime = context.getBean(MultiAgentRuntime.class);

                    assertTrue(registry.get("alpha").isPresent());
                    assertTrue(registry.get("beta").isPresent());

                    MultiAgentResponse response = runtime.run(MultiAgentRequest.of(
                            "session-multi",
                            "user-1",
                            "coordinate"
                    ));

                    assertEquals(MultiAgentStatus.SUCCESS, response.status());
                    assertEquals(2, response.results().size());
                    assertTrue(response.output().contains("alpha"));
                    assertTrue(response.output().contains("beta"));
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
    void registersToolkitToolsWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "openharness.toolkit.base-directory=.",
                        "openharness.toolkit.file.enabled=true",
                        "openharness.toolkit.file.allowed-paths[0]=.",
                        "openharness.toolkit.shell.enabled=true",
                        "openharness.toolkit.shell.allowed-prefixes[0]=printf ",
                        "openharness.toolkit.search.enabled=true",
                        "openharness.toolkit.mcp.enabled=true"
                )
                .run(context -> {
                    ToolRegistry registry = context.getBean(ToolRegistry.class);

                    assertTrue(registry.get("file").isPresent());
                    assertTrue(registry.get("shell").isPresent());
                    assertTrue(registry.get("search").isPresent());
                    assertTrue(registry.get("mcp_call").isPresent());
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

    @Test
    void registersSkillDefinitionBeansAndCreatesSkillExecutor() {
        contextRunner
                .withUserConfiguration(SkillBeanConfiguration.class)
                .run(context -> {
                    SkillRegistry registry = context.getBean(SkillRegistry.class);
                    SkillExecutor executor = context.getBean(SkillExecutor.class);
                    CountingTool tool = context.getBean(CountingTool.class);

                    assertTrue(registry.get("starter_bean_skill", "0.1.0").isPresent());

                    SkillRunResponse response = executor.run(SkillRunRequest.of(
                            "starter_bean_skill",
                            "session-skill",
                            "user-1",
                            Map.of("text", "hello")
                    ));

                    assertEquals(SkillRunStatus.SUCCESS, response.status());
                    assertEquals(1, tool.executions());
                    assertEquals(1, response.toolCalls().size());
                    assertFalse(response.traceId().isBlank());
                });
    }

    @Test
    void loadsYamlSkillsFromConfiguredLocations() {
        contextRunner
                .withUserConfiguration(ToolBeanConfiguration.class)
                .withPropertyValues("openharness.skill.yaml-locations=classpath:/skills/*.yaml")
                .run(context -> {
                    SkillRegistry registry = context.getBean(SkillRegistry.class);
                    SkillExecutor executor = context.getBean(SkillExecutor.class);
                    CountingTool tool = context.getBean(CountingTool.class);

                    assertTrue(registry.get("yaml_starter_skill", "0.1.0").isPresent());

                    SkillRunResponse response = executor.run(SkillRunRequest.of(
                            "yaml_starter_skill",
                            "session-yaml-skill",
                            "user-1",
                            Map.of("text", "yaml hello")
                    ));

                    assertEquals(SkillRunStatus.SUCCESS, response.status());
                    assertEquals(1, tool.executions());
                    assertEquals("ok", response.output());
                });
    }

    @Test
    void loadsMarkdownSkillsFromConfiguredLocations() {
        contextRunner
                .withUserConfiguration(ToolBeanConfiguration.class)
                .withPropertyValues("openharness.skill.markdown-locations=classpath:/skills/*.md")
                .run(context -> {
                    SkillRegistry registry = context.getBean(SkillRegistry.class);
                    SkillExecutor executor = context.getBean(SkillExecutor.class);
                    CountingTool tool = context.getBean(CountingTool.class);

                    assertTrue(registry.get("markdown_starter_skill", "0.1.0").isPresent());

                    SkillRunResponse response = executor.run(SkillRunRequest.of(
                            "markdown_starter_skill",
                            "session-md-skill",
                            "user-1",
                            Map.of("text", "markdown hello")
                    ));

                    assertEquals(SkillRunStatus.SUCCESS, response.status());
                    assertEquals(1, tool.executions());
                    assertEquals("ok", response.output());
                });
    }

    @Test
    void contextFilesInjectInstructions(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("CLAUDE.md"), "Prefer concise Java answers.");
        contextRunner
                .withUserConfiguration(ContextFileLlmConfiguration.class)
                .withPropertyValues(
                        "openharness.memory.context-files.enabled=true",
                        "openharness.memory.context-files.base-directory=" + tempDir
                )
                .run(context -> {
                    AgentRuntime runtime = context.getBean(AgentRuntime.class);
                    ContextFileLlmConfiguration.CapturingLlmAdapter adapter =
                            context.getBean(ContextFileLlmConfiguration.CapturingLlmAdapter.class);

                    runtime.run(AgentRequest.of("session-context-files", "user-1", "hello"));

                    assertTrue(adapter.messages().stream().anyMatch(message -> message.content().contains("CLAUDE.md")));
                    assertTrue(adapter.messages().stream().anyMatch(message -> message.content().contains("Prefer concise")));
                });
    }

    @Test
    void providerProfilesCreateLlmAdapterAndRegistry() {
        contextRunner
                .withPropertyValues(
                        "openharness.provider.enabled=true",
                        "openharness.provider.default-profile=local",
                        "openharness.provider.profiles[0].name=local",
                        "openharness.provider.profiles[0].endpoint=http://localhost:11434/v1/chat/completions",
                        "openharness.provider.profiles[0].model=llama3.1"
                )
                .run(context -> {
                    assertTrue(context.containsBean("configuredLlmAdapter"));
                    assertTrue(context.containsBean("agentRuntime"));
                    assertTrue(context.getBean(LLMAdapterRegistry.class).get("local").isPresent());
                });
    }

    static class TextOnlyLlmConfiguration {
        @Bean
        LLMAdapter llmAdapter() {
            return (messages, tools) -> LLMResponse.text("starter ok");
        }
    }

    static class NamedLlmConfiguration {
        @Bean
        NamedLLMAdapter namedLLMAdapter() {
            return new NamedLLMAdapter("primary", (messages, tools) -> LLMResponse.text("primary"));
        }
    }

    static class PluginConfiguration {
        @Bean
        OpenHarnessPlugin openHarnessPlugin() {
            return new OpenHarnessPlugin() {
                @Override
                public PluginDescriptor descriptor() {
                    return new PluginDescriptor("starter-plugin", "1.0.0", "Starter Plugin");
                }

                @Override
                public void activate(PluginContext context) {
                    context.toolRegistry().register(new Tool() {
                        @Override
                        public String name() {
                            return "plugin_tool";
                        }

                        @Override
                        public String description() {
                            return "Plugin tool";
                        }

                        @Override
                        public ToolResult execute(ToolContext context) {
                            return ToolResult.success("plugin ok");
                        }
                    });
                }
            };
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

    static class SkillBeanConfiguration {
        @Bean
        CountingTool countingTool() {
            return new CountingTool();
        }

        @Bean
        SkillDefinition skillDefinition() {
            return SkillDefinition.builder("starter_bean_skill", "0.1.0")
                    .requiredTool("echo")
                    .toolStep("echo_text", "echo", Map.of("text", "{{text}}"))
                    .build();
        }

        @Bean
        LLMAdapter llmAdapter() {
            return (messages, tools) -> LLMResponse.text("unused");
        }
    }

    static class TaskBeanConfiguration {
        @Bean
        TaskHandler taskHandler() {
            return new TaskHandler() {
                @Override
                public String type() {
                    return "starter_task";
                }

                @Override
                public TaskResult handle(io.openharness4j.task.TaskContext context) {
                    return TaskResult.success("task " + context.input().get("name") + " completed");
                }
            };
        }
    }

    static class MultiAgentConfiguration {
        @Bean
        SubAgentDefinition alphaSubAgent() {
            return new SubAgentDefinition("alpha", "Alpha", request ->
                    new AgentResponse("alpha=done", List.of(), io.openharness4j.api.Usage.zero(), "trace-alpha", FinishReason.STOP)
            );
        }

        @Bean
        SubAgentDefinition betaSubAgent() {
            return new SubAgentDefinition("beta", "Beta", request ->
                    new AgentResponse("beta=done", List.of(), io.openharness4j.api.Usage.zero(), "trace-beta", FinishReason.STOP)
            );
        }

        @Bean
        PlanningAgent planningAgent() {
            return (request, agents) -> List.of(
                    AgentTask.of("alpha", "alpha work"),
                    AgentTask.of("beta", "beta work")
            );
        }
    }

    private static TaskSnapshot awaitTaskStatus(TaskEngine engine, String taskId, TaskStatus status) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        TaskSnapshot latest = engine.get(taskId).orElseThrow();
        while (System.nanoTime() < deadline) {
            latest = engine.get(taskId).orElseThrow();
            if (latest.status() == status) {
                return latest;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("expected " + status + " but was " + latest.status());
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

    static class ContextFileLlmConfiguration {
        @Bean
        CapturingLlmAdapter llmAdapter() {
            return new CapturingLlmAdapter();
        }

        static class CapturingLlmAdapter implements LLMAdapter {
            private final AtomicReference<List<io.openharness4j.api.Message>> messages = new AtomicReference<>(List.of());

            @Override
            public LLMResponse chat(List<io.openharness4j.api.Message> messages, List<io.openharness4j.api.ToolDefinition> tools) {
                this.messages.set(messages);
                return LLMResponse.text("context ok");
            }

            List<io.openharness4j.api.Message> messages() {
                return messages.get();
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
