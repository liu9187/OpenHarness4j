package io.openharness4j.skill;

import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.PermissionDecision;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.ToolResult;
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSkillExecutorTest {

    @Test
    void runsJavaDslSkillWithSequentialToolAndLlmSteps() {
        QueryOrderTool queryOrderTool = new QueryOrderTool();
        ClassifyFailureTool classifyFailureTool = new ClassifyFailureTool();
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(queryOrderTool);
        toolRegistry.register(classifyFailureTool);

        InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
        skillRegistry.register(orderDiagnosisSkill());
        AtomicInteger permissionChecks = new AtomicInteger();
        AtomicReference<String> llmPrompt = new AtomicReference<>();
        LLMAdapter llmAdapter = (messages, tools) -> {
            llmPrompt.set(messages.get(messages.size() - 1).content());
            return LLMResponse.text("summary: payment timeout");
        };
        PermissionChecker permissionChecker = (call, context) -> {
            permissionChecks.incrementAndGet();
            return PermissionDecision.allow();
        };

        SkillRunResponse response = executor(skillRegistry, toolRegistry, llmAdapter, permissionChecker)
                .run(SkillRunRequest.of("order_diagnosis", "session-skill", "user-1", Map.of("orderId", "ORD-1")));

        assertEquals(SkillRunStatus.SUCCESS, response.status());
        assertEquals("summary: payment timeout", response.output());
        assertEquals(3, response.steps().size());
        assertEquals(2, response.toolCalls().size());
        assertEquals(2, permissionChecks.get());
        assertEquals("ORD-1", queryOrderTool.orderId());
        assertEquals("order ORD-1 failed", classifyFailureTool.order());
        assertTrue(llmPrompt.get().contains("payment timeout"));
        assertFalse(response.traceId().isBlank());
        assertTrue(response.steps().stream().allMatch(step -> step.traceId().equals(response.traceId())));
    }

    @Test
    void loadsYamlSkillAndRunsIt() {
        QueryOrderTool queryOrderTool = new QueryOrderTool();
        ClassifyFailureTool classifyFailureTool = new ClassifyFailureTool();
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(queryOrderTool);
        toolRegistry.register(classifyFailureTool);
        SkillDefinition definition = new YamlSkillLoader().load(new ByteArrayInputStream(yamlSkill().getBytes(StandardCharsets.UTF_8)));
        InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
        skillRegistry.register(definition);

        SkillRunResponse response = executor(skillRegistry, toolRegistry, (messages, tools) -> LLMResponse.text("yaml summary"), null)
                .run(SkillRunRequest.of("order_diagnosis_yaml", "session-yaml", "user-1", Map.of("orderId", "ORD-2")));

        assertEquals(SkillRunStatus.SUCCESS, response.status());
        assertEquals("yaml summary", response.output());
        assertEquals("ORD-2", queryOrderTool.orderId());
        assertEquals("order ORD-2 failed", classifyFailureTool.order());
    }

    @Test
    void returnsPermissionDeniedWhenToolStepIsDenied() {
        CountingTool tool = new CountingTool();
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(tool);
        InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
        skillRegistry.register(SkillDefinition.builder("blocked_skill", "0.1.0")
                .requiredTool("echo")
                .toolStep("echo_text", "echo", Map.of("text", "secret"))
                .build());

        SkillRunResponse response = executor(
                skillRegistry,
                toolRegistry,
                (messages, tools) -> LLMResponse.text("unused"),
                (call, context) -> PermissionDecision.deny("blocked", RiskLevel.HIGH)
        ).run(SkillRunRequest.of("blocked_skill", "session-denied", "user-1", Map.of()));

        assertEquals(SkillRunStatus.PERMISSION_DENIED, response.status());
        assertEquals(0, tool.executions());
        assertEquals(1, response.toolCalls().size());
        assertEquals(ToolResultStatus.PERMISSION_DENIED, response.toolCalls().get(0).status());
        assertEquals("PERMISSION_DENIED", response.errorCode());
    }

    @Test
    void validatesRequiredInputBeforeExecutingWorkflow() {
        InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
        skillRegistry.register(orderDiagnosisSkill());

        SkillRunResponse response = executor(skillRegistry, new InMemoryToolRegistry(), (messages, tools) -> LLMResponse.text("unused"), null)
                .run(SkillRunRequest.of("order_diagnosis", "session-invalid", "user-1", Map.of()));

        assertEquals(SkillRunStatus.INVALID_INPUT, response.status());
        assertEquals("INVALID_INPUT", response.errorCode());
        assertTrue(response.steps().isEmpty());
    }

    @Test
    void validatesToolDependenciesBeforeExecutingWorkflow() {
        InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
        skillRegistry.register(SkillDefinition.builder("missing_tool_skill", "0.1.0")
                .requiredTool("missing_tool")
                .toolStep("missing", "missing_tool", Map.of())
                .build());

        SkillRunResponse response = executor(skillRegistry, new InMemoryToolRegistry(), (messages, tools) -> LLMResponse.text("unused"), null)
                .run(SkillRunRequest.of("missing_tool_skill", "session-missing", "user-1", Map.of()));

        assertEquals(SkillRunStatus.FAILED, response.status());
        assertEquals("SKILL_TOOL_NOT_FOUND", response.errorCode());
        assertTrue(response.steps().isEmpty());
    }

    private static SkillDefinition orderDiagnosisSkill() {
        return SkillDefinition.builder("order_diagnosis", "0.1.0")
                .name("Order Diagnosis")
                .inputSchema(Map.of("type", "object", "required", List.of("orderId")))
                .prompt("You are an order operation assistant.", "Diagnose {{orderId}}.")
                .requiredTools(List.of("query_order", "classify_failure"))
                .toolStep("query_order", "query_order", Map.of("orderId", "{{orderId}}"))
                .toolStep("classify_failure", "classify_failure", Map.of("order", "{{steps.query_order.output}}"))
                .llmStep("summarize", "Summarize {{steps.classify_failure.output}}.")
                .build();
    }

    private static DefaultSkillExecutor executor(
            SkillRegistry skillRegistry,
            InMemoryToolRegistry toolRegistry,
            LLMAdapter llmAdapter,
            PermissionChecker permissionChecker
    ) {
        return new DefaultSkillExecutor(
                skillRegistry,
                llmAdapter,
                toolRegistry,
                permissionChecker,
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );
    }

    private static String yamlSkill() {
        return """
                id: order_diagnosis_yaml
                version: 0.1.0
                name: Order Diagnosis YAML
                inputSchema:
                  type: object
                  required:
                    - orderId
                prompt:
                  system: You are an order operation assistant.
                  user: Diagnose {{orderId}}.
                requiredTools:
                  - query_order
                  - classify_failure
                workflow:
                  - name: query_order
                    type: tool
                    tool: query_order
                    args:
                      orderId: "{{orderId}}"
                  - name: classify_failure
                    type: tool
                    tool: classify_failure
                    args:
                      order: "{{steps.query_order.output}}"
                  - name: summarize
                    type: llm
                    prompt: "Summarize {{steps.classify_failure.output}}."
                """;
    }

    private static final class QueryOrderTool implements Tool {
        private final AtomicReference<String> orderId = new AtomicReference<>();

        @Override
        public String name() {
            return "query_order";
        }

        @Override
        public String description() {
            return "Query order";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            orderId.set(String.valueOf(context.args().get("orderId")));
            return ToolResult.success("order " + orderId.get() + " failed");
        }

        String orderId() {
            return orderId.get();
        }
    }

    private static final class ClassifyFailureTool implements Tool {
        private final AtomicReference<String> order = new AtomicReference<>();

        @Override
        public String name() {
            return "classify_failure";
        }

        @Override
        public String description() {
            return "Classify failure";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(name(), description(), Map.of());
        }

        @Override
        public ToolResult execute(ToolContext context) {
            order.set(String.valueOf(context.args().get("order")));
            return ToolResult.success("payment timeout");
        }

        String order() {
            return order.get();
        }
    }

    private static final class CountingTool implements Tool {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echo";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            executions.incrementAndGet();
            return ToolResult.success(String.valueOf(context.args().get("text")));
        }

        int executions() {
            return executions.get();
        }
    }
}
