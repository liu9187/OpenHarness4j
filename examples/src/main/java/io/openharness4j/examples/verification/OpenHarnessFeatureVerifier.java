package io.openharness4j.examples.verification;

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
import io.openharness4j.api.ToolResultStatus;
import io.openharness4j.api.Usage;
import io.openharness4j.llm.FallbackLLMAdapter;
import io.openharness4j.llm.InMemoryLLMAdapterRegistry;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.LLMAdapterException;
import io.openharness4j.llm.LLMProviderProfileSelector;
import io.openharness4j.llm.MockLLMAdapter;
import io.openharness4j.memory.ContextFileContextManager;
import io.openharness4j.memory.InMemoryMemoryStore;
import io.openharness4j.memory.MemoryContextManager;
import io.openharness4j.memory.MemorySessionManager;
import io.openharness4j.memory.MemoryWindowPolicy;
import io.openharness4j.memory.SimpleMemorySummarizer;
import io.openharness4j.multiagent.AgentTask;
import io.openharness4j.multiagent.DefaultMultiAgentRuntime;
import io.openharness4j.multiagent.InMemorySubAgentRegistry;
import io.openharness4j.multiagent.MultiAgentRequest;
import io.openharness4j.multiagent.MultiAgentResponse;
import io.openharness4j.multiagent.MultiAgentStatus;
import io.openharness4j.multiagent.PlanningAgent;
import io.openharness4j.multiagent.SubAgentDefinition;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.observability.ExportingAgentTracer;
import io.openharness4j.observability.InMemoryObservationExporter;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.ApprovalRequiredToolHook;
import io.openharness4j.permission.AuditingPermissionChecker;
import io.openharness4j.permission.CommandPermissionPolicy;
import io.openharness4j.permission.CommandPermissionRule;
import io.openharness4j.permission.InMemoryPermissionAuditStore;
import io.openharness4j.permission.PathAccessMode;
import io.openharness4j.permission.PathAccessPolicy;
import io.openharness4j.permission.PathAccessRule;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.permission.PermissionPolicy;
import io.openharness4j.permission.PolicyPermissionChecker;
import io.openharness4j.permission.ToolApprovalDecision;
import io.openharness4j.personal.DefaultPersonalAgentService;
import io.openharness4j.personal.PersonalAgentMessage;
import io.openharness4j.personal.PersonalAgentSubmission;
import io.openharness4j.personal.PersonalAgentTaskSnapshot;
import io.openharness4j.personal.channel.SlackChannelAdapter;
import io.openharness4j.personal.team.InMemoryTeamAgentRegistry;
import io.openharness4j.personal.team.InMemoryTeamRuntime;
import io.openharness4j.personal.team.TeamAgentArchive;
import io.openharness4j.personal.team.TeamAgentDefinition;
import io.openharness4j.personal.team.TeamAgentRequest;
import io.openharness4j.personal.team.TeamAgentSnapshot;
import io.openharness4j.personal.team.TeamAgentSubmission;
import io.openharness4j.personal.team.TeamRuntime;
import io.openharness4j.plugin.InMemoryPluginRegistry;
import io.openharness4j.plugin.OpenHarnessPlugin;
import io.openharness4j.plugin.PluginContext;
import io.openharness4j.plugin.PluginDescriptor;
import io.openharness4j.plugin.PluginManager;
import io.openharness4j.plugin.PluginStatus;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.AgentEvent;
import io.openharness4j.runtime.AgentEventType;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.runtime.RetryPolicy;
import io.openharness4j.runtime.TokenPricingCostEstimator;
import io.openharness4j.skill.DefaultSkillExecutor;
import io.openharness4j.skill.InMemorySkillRegistry;
import io.openharness4j.skill.MarkdownSkillLoader;
import io.openharness4j.skill.SkillDefinition;
import io.openharness4j.skill.SkillRunRequest;
import io.openharness4j.skill.SkillRunResponse;
import io.openharness4j.skill.SkillRunStatus;
import io.openharness4j.task.InMemoryTaskEngine;
import io.openharness4j.task.InMemoryTaskRegistry;
import io.openharness4j.task.TaskContext;
import io.openharness4j.task.TaskHandler;
import io.openharness4j.task.TaskRequest;
import io.openharness4j.task.TaskResult;
import io.openharness4j.task.TaskSnapshot;
import io.openharness4j.task.TaskStatus;
import io.openharness4j.task.TaskSubmission;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;
import io.openharness4j.toolkit.FileTool;
import io.openharness4j.toolkit.InMemorySearchProvider;
import io.openharness4j.toolkit.McpClientTool;
import io.openharness4j.toolkit.SearchResult;
import io.openharness4j.toolkit.SearchTool;
import io.openharness4j.toolkit.ShellTool;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
        results.add(verifiesSkillWorkflow());
        results.add(verifiesTaskEngine());
        results.add(verifiesMultiAgent());
        results.add(verifiesProductionRuntime());
        results.add(verifiesRuntimeExecutionParity());
        results.add(verifiesToolkitGovernanceParity());
        results.add(verifiesSkillsContextProviderProfiles());
        results.add(verifiesPersonalAgentTeamRuntime());
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

    private static VerificationResult verifiesSkillWorkflow() {
        EchoTool echoTool = new EchoTool();
        MathTool mathTool = new MathTool();
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        toolRegistry.register(echoTool);
        toolRegistry.register(mathTool);

        InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
        skillRegistry.register(SkillDefinition.builder("demo_skill", "0.3.0")
                .name("Demo Skill")
                .inputSchema(Map.of("type", "object", "required", List.of("text", "left", "right")))
                .requiredTools(List.of("echo", "add"))
                .toolStep("echo_text", "echo", Map.of("text", "{{text}}"))
                .toolStep("add_numbers", "add", Map.of("left", "{{left}}", "right", "{{right}}"))
                .llmStep("summarize", "Summarize {{steps.echo_text.output}} and {{steps.add_numbers.output}}.")
                .build());

        LLMAdapter llmAdapter = (messages, tools) -> {
            String prompt = messages.get(messages.size() - 1).content();
            expect(prompt.contains("skill input"), "skill LLM step should receive previous tool output");
            expect(prompt.contains("sum=5.0"), "skill LLM step should receive add output");
            return LLMResponse.text("skill workflow completed");
        };
        DefaultSkillExecutor executor = new DefaultSkillExecutor(
                skillRegistry,
                llmAdapter,
                toolRegistry,
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );

        SkillRunResponse response = executor.run(SkillRunRequest.of(
                "demo_skill",
                "session-skill",
                "user-1",
                Map.of("text", "skill input", "left", 2, "right", 3)
        ));

        expect(response.status() == SkillRunStatus.SUCCESS, "skill workflow should succeed");
        expect(response.output().equals("skill workflow completed"), "skill workflow final output mismatch");
        expect(echoTool.executions() == 1, "skill echo step should execute once");
        expect(mathTool.executions() == 1, "skill add step should execute once");
        expect(response.toolCalls().size() == 2, "skill workflow should record two tool calls");
        return new VerificationResult(
                "skill workflow",
                FinishReason.STOP,
                response.toolCalls().size(),
                response.usage().totalTokens(),
                response.traceId(),
                "Prompt + workflow skill executes two tool steps and one LLM step"
        );
    }

    private static VerificationResult verifiesTaskEngine() {
        InMemoryTaskRegistry taskRegistry = new InMemoryTaskRegistry();
        CountDownLatch reportStarted = new CountDownLatch(1);
        CountDownLatch releaseReport = new CountDownLatch(1);
        CountDownLatch cancelStarted = new CountDownLatch(1);
        CountDownLatch timeoutStarted = new CountDownLatch(1);
        taskRegistry.register(taskHandler("daily_report", context -> {
            reportStarted.countDown();
            releaseReport.await(1, TimeUnit.SECONDS);
            return TaskResult.success("report ready", Map.of("name", context.input().get("name")));
        }));
        taskRegistry.register(taskHandler("cancellable_task", context -> {
            cancelStarted.countDown();
            while (!context.cancellationRequested()) {
                Thread.sleep(10);
            }
            return TaskResult.success("cancel loop ended");
        }));
        taskRegistry.register(taskHandler("timeout_task", context -> {
            timeoutStarted.countDown();
            Thread.sleep(1_000);
            return TaskResult.success("too late");
        }));

        try (InMemoryTaskEngine taskEngine = new InMemoryTaskEngine(taskRegistry, 0, 2)) {
            TaskSubmission report = taskEngine.submit(TaskRequest.of("daily_report", Map.of("name", "daily")));
            expect(awaitLatch(reportStarted), "report task should start");
            expect(taskEngine.get(report.taskId()).orElseThrow().status() == TaskStatus.RUNNING, "report task should be queryable as running");
            releaseReport.countDown();
            TaskSnapshot completed = awaitTaskStatus(taskEngine, report.taskId(), TaskStatus.SUCCEEDED);

            TaskSubmission cancellable = taskEngine.submit(TaskRequest.of("cancellable_task", Map.of()));
            expect(awaitLatch(cancelStarted), "cancellable task should start");
            expect(taskEngine.cancel(cancellable.taskId()), "cancellable task should accept cancellation");
            awaitTaskStatus(taskEngine, cancellable.taskId(), TaskStatus.CANCELLED);

            TaskSubmission timeout = taskEngine.submit(TaskRequest.withTimeout("timeout_task", Map.of(), 50));
            expect(awaitLatch(timeoutStarted), "timeout task should start");
            awaitTaskStatus(taskEngine, timeout.taskId(), TaskStatus.TIMED_OUT);

            return new VerificationResult(
                    "task engine",
                    FinishReason.STOP,
                    0,
                    0,
                    completed.taskId(),
                    "async task status query, cancellation and timeout all work"
            );
        }
    }

    private static VerificationResult verifiesMultiAgent() {
        InMemorySubAgentRegistry subAgents = new InMemorySubAgentRegistry();
        AtomicReference<String> riskInstruction = new AtomicReference<>();
        AtomicReference<String> financeInstruction = new AtomicReference<>();
        subAgents.register(new SubAgentDefinition("risk_agent", "Assess operational risk", request -> {
            riskInstruction.set(request.input());
            return new AgentResponse("decision=approve\nrisk=low", List.of(), Usage.zero(), "trace-risk", FinishReason.STOP);
        }));
        subAgents.register(new SubAgentDefinition("finance_agent", "Assess financial exposure", request -> {
            financeInstruction.set(request.input());
            return new AgentResponse("decision=reject\nfinance=high", List.of(), Usage.zero(), "trace-finance", FinishReason.STOP);
        }));
        AtomicInteger planningCalls = new AtomicInteger();
        PlanningAgent planner = (request, agents) -> {
            planningCalls.incrementAndGet();
            return List.of(
                    new AgentTask("risk_task", "risk_agent", "Assess launch risk", Map.of()),
                    new AgentTask("finance_task", "finance_agent", "Assess launch finance", Map.of())
            );
        };

        MultiAgentResponse response = new DefaultMultiAgentRuntime(subAgents, planner, null, null)
                .run(MultiAgentRequest.of("session-multi-agent", "user-1", "Should we launch?"));

        expect(response.status() == MultiAgentStatus.CONFLICT, "multi-agent response should surface conflict");
        expect(planningCalls.get() == 1, "planning agent should split the task once");
        expect(response.tasks().size() == 2, "planning should create two sub tasks");
        expect(response.results().size() == 2, "two sub agents should execute");
        expect(response.conflicts().size() == 1, "conflicting decision values should be detected");
        expect("Assess launch risk".equals(riskInstruction.get()), "risk agent should receive independent instruction");
        expect("Assess launch finance".equals(financeInstruction.get()), "finance agent should receive independent instruction");
        return new VerificationResult(
                "multi-agent",
                FinishReason.STOP,
                response.toolCalls().size(),
                response.usage().totalTokens(),
                response.conflicts().get(0).key(),
                "planning splits work, sub agents execute, aggregation reports conflict"
        );
    }

    private static VerificationResult verifiesProductionRuntime() {
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        AtomicInteger pluginToolExecutions = new AtomicInteger();
        InMemoryPluginRegistry pluginRegistry = new InMemoryPluginRegistry();
        PluginContext pluginContext = new PluginContext(
                toolRegistry,
                new InMemorySkillRegistry(),
                new InMemoryTaskRegistry(),
                new InMemorySubAgentRegistry()
        );
        OpenHarnessPlugin plugin = new OpenHarnessPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor("verification-plugin", "1.0.0", "Verification Plugin");
            }

            @Override
            public void activate(PluginContext context) {
                context.toolRegistry().register(new Tool() {
                    @Override
                    public String name() {
                        return "prod_echo";
                    }

                    @Override
                    public String description() {
                        return "Production verification echo.";
                    }

                    @Override
                    public ToolResult execute(ToolContext context) {
                        pluginToolExecutions.incrementAndGet();
                        return ToolResult.success("prod=" + context.args().get("text"));
                    }
                });
            }
        };
        new PluginManager(pluginRegistry, pluginContext, List.of(plugin)).activateAll();

        InMemoryPermissionAuditStore auditStore = new InMemoryPermissionAuditStore();
        PermissionChecker permissionChecker = new AuditingPermissionChecker(
                new PolicyPermissionChecker(PermissionPolicy.allowByDefault(List.of())),
                auditStore
        );
        InMemoryObservationExporter observationExporter = new InMemoryObservationExporter();
        LLMAdapter primary = (messages, tools) -> {
            throw new LLMAdapterException("primary unavailable");
        };
        LLMAdapter fallback = new MockLLMAdapter(List.of(
                LLMResponse.toolCalls(
                        "calling prod echo",
                        List.of(new ToolCall("call_prod_echo", "prod_echo", Map.of("text", "ready", "token", "secret")))
                ),
                LLMResponse.text("production runtime completed")
        ));
        AgentRuntime runtime = new DefaultAgentRuntime(
                new FallbackLLMAdapter(List.of(primary, fallback)),
                toolRegistry,
                permissionChecker,
                new ExportingAgentTracer(observationExporter),
                new DefaultContextManager(),
                AgentRuntimeConfig.defaults()
        );

        AgentResponse response = runtime.run(AgentRequest.of("session-prod", "user-1", "verify production runtime"));

        expect(response.finishReason() == FinishReason.STOP, "production runtime should stop");
        expect("production runtime completed".equals(response.content()), "fallback adapter should provide final response");
        expect(pluginRegistry.get("verification-plugin").orElseThrow().status() == PluginStatus.ACTIVE, "plugin should be active");
        expect(pluginToolExecutions.get() == 1, "plugin tool should execute once");
        expect(auditStore.list().size() == 1, "permission audit should record one decision");
        expect(Boolean.TRUE.equals(auditStore.list().get(0).allowed()), "permission audit should allow tool");
        expect("***".equals(auditStore.list().get(0).args().get("token")), "permission audit should redact token");
        expect(response.toolCalls().size() == 1, "production runtime should record tool call");
        expect("***".equals(response.toolCalls().get(0).args().get("token")), "trace should redact token");
        expect(observationExporter.list().size() == 1, "observation exporter should receive one observation");
        return result(
                "production runtime",
                response,
                "audit, plugin, fallback model and observation export work together"
        );
    }

    private static VerificationResult verifiesRuntimeExecutionParity() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        AtomicInteger llmAttempts = new AtomicInteger();
        AtomicInteger flakyAttempts = new AtomicInteger();
        AtomicInteger activeSlowTools = new AtomicInteger();
        AtomicInteger maxActiveSlowTools = new AtomicInteger();
        List<AgentEvent> events = new CopyOnWriteArrayList<>();

        registry.register(new Tool() {
            @Override
            public String name() {
                return "flaky_tool";
            }

            @Override
            public String description() {
                return "Fails once and then succeeds.";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                if (flakyAttempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("temporary tool failure");
                }
                return ToolResult.success("flaky recovered");
            }
        });
        registry.register(slowTool("slow_one", activeSlowTools, maxActiveSlowTools));
        registry.register(slowTool("slow_two", activeSlowTools, maxActiveSlowTools));

        LLMAdapter llmAdapter = (messages, tools) -> {
            int attempt = llmAttempts.incrementAndGet();
            if (attempt == 1) {
                throw new LLMAdapterException("temporary llm outage");
            }
            boolean hasToolResults = messages.stream().anyMatch(message -> message.toolCallId() != null && !message.toolCallId().isBlank());
            if (!hasToolResults) {
                return new LLMResponse(
                        Message.assistant("calling runtime parity tools"),
                        List.of(
                                new ToolCall("call_flaky", "flaky_tool", Map.of()),
                                new ToolCall("call_slow_one", "slow_one", Map.of()),
                                new ToolCall("call_slow_two", "slow_two", Map.of())
                        ),
                        new Usage(100, 20, 120)
                );
            }
            return new LLMResponse(
                    Message.assistant("runtime execution parity completed"),
                    List.of(),
                    new Usage(30, 10, 40)
            );
        };
        AgentRuntimeConfig config = new AgentRuntimeConfig(
                AgentRuntimeConfig.DEFAULT_MAX_ITERATIONS,
                true,
                RetryPolicy.fixedDelay(2, 0),
                RetryPolicy.fixedDelay(2, 0),
                new TokenPricingCostEstimator("USD", new BigDecimal("1.00"), new BigDecimal("2.00"))
        );
        AgentRuntime runtime = runtime(llmAdapter, registry, new AllowAllPermissionChecker(), config);

        AgentResponse response = runtime.run(
                AgentRequest.of("session-runtime-parity", "user-1", "verify runtime execution parity"),
                events::add
        );

        expect(response.finishReason() == FinishReason.STOP, "runtime parity scenario should stop");
        expect("runtime execution parity completed".equals(response.content()), "runtime parity final content mismatch");
        expect(llmAttempts.get() == 3, "LLM should fail once, then produce tool calls and final text");
        expect(flakyAttempts.get() == 2, "flaky tool should be retried once");
        expect(maxActiveSlowTools.get() > 1, "slow tools should run in parallel");
        expect(response.toolCalls().size() == 3, "runtime parity should record three tool calls");
        expect(response.toolCalls().get(0).toolName().equals("flaky_tool"), "tool call records should keep model order");
        expect(events.stream().anyMatch(event -> event.type() == AgentEventType.STARTED), "stream should include STARTED");
        expect(events.stream().anyMatch(event -> event.type() == AgentEventType.LLM_RETRY), "stream should include LLM_RETRY");
        expect(events.stream().anyMatch(event -> event.type() == AgentEventType.TOOL_RETRY), "stream should include TOOL_RETRY");
        expect(events.stream().filter(event -> event.type() == AgentEventType.TOOL_STARTED).count() == 3, "stream should include tool starts");
        expect(events.stream().filter(event -> event.type() == AgentEventType.TOOL_DONE).count() == 3, "stream should include tool completions");
        expect(events.stream().anyMatch(event -> event.type() == AgentEventType.TEXT_DELTA), "stream should include text delta");
        expect(events.stream().anyMatch(event -> event.type() == AgentEventType.DONE), "stream should include DONE");
        AgentEvent costEvent = events.stream()
                .filter(event -> event.type() == AgentEventType.COST_UPDATED && !event.cost().isZero())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("stream should include non-zero cost"));
        expect(costEvent.cost().amount().signum() > 0, "cost should be greater than zero");
        return result(
                "runtime execution parity",
                response,
                "streaming events, retry, parallel tools and cost tracking work together"
        );
    }

    private static VerificationResult verifiesToolkitGovernanceParity() {
        Path baseDirectory;
        try {
            baseDirectory = Files.createTempDirectory("openharness4j-toolkit");
            Files.createDirectories(baseDirectory.resolve("secret"));
            Files.writeString(baseDirectory.resolve("secret/data.txt"), "classified");
        } catch (IOException ex) {
            throw new IllegalStateException("failed to prepare toolkit temp directory", ex);
        }

        try {
            InMemoryToolRegistry registry = new InMemoryToolRegistry();
            registry.register(new FileTool(baseDirectory, PathAccessPolicy.denyByDefault(List.of(
                    PathAccessRule.deny(
                            baseDirectory.resolve("secret"),
                            Set.of(PathAccessMode.READ),
                            RiskLevel.HIGH,
                            "secret path denied"
                    ),
                    PathAccessRule.allow(
                            baseDirectory,
                            Set.of(PathAccessMode.READ, PathAccessMode.WRITE, PathAccessMode.LIST)
                    )
            ))));
            registry.register(new ShellTool(
                    baseDirectory,
                    CommandPermissionPolicy.denyByDefault(List.of(
                            CommandPermissionRule.denyContains("rm -rf", RiskLevel.HIGH, "destructive command denied"),
                            CommandPermissionRule.allowPrefix("printf ")
                    ))
            ));
            registry.register(new SearchTool(new InMemorySearchProvider(List.of(
                    new SearchResult("OpenHarness4j Toolkit", "https://example.test/toolkit", "Governed Java tools")
            ))));
            registry.register(new McpClientTool(request -> ToolResult.success(
                    "mcp " + request.server() + "/" + request.method(),
                    Map.of("params", request.params())
            )));

            AtomicInteger approvalRequests = new AtomicInteger();
            ApprovalRequiredToolHook approvalHook = new ApprovalRequiredToolHook(
                    Set.of("shell"),
                    RiskLevel.HIGH,
                    "shell approval required",
                    request -> {
                        approvalRequests.incrementAndGet();
                        return ToolApprovalDecision.approve("approved in verification");
                    }
            );

            AgentRuntime runtime = new DefaultAgentRuntime(
                    new MockLLMAdapter(List.of(
                            LLMResponse.toolCalls(
                                    "calling governed toolkit",
                                    List.of(
                                            new ToolCall("call_file_write", "file", Map.of(
                                                    "operation", "write",
                                                    "path", "notes.txt",
                                                    "content", "toolkit ready"
                                            )),
                                            new ToolCall("call_file_secret", "file", Map.of(
                                                    "operation", "read",
                                                    "path", "secret/data.txt"
                                            )),
                                            new ToolCall("call_shell_ok", "shell", Map.of("command", "printf toolkit")),
                                            new ToolCall("call_shell_blocked", "shell", Map.of("command", "rm -rf /tmp/example")),
                                            new ToolCall("call_search", "search", Map.of("query", "governed")),
                                            new ToolCall("call_mcp", "mcp_call", Map.of(
                                                    "server", "local",
                                                    "method", "tools/list",
                                                    "params", Map.of("cursor", "next")
                                            ))
                                    )
                            ),
                            LLMResponse.text("toolkit governance completed")
                    )),
                    registry,
                    new AllowAllPermissionChecker(),
                    new DefaultAgentTracer(),
                    new DefaultContextManager(),
                    AgentRuntimeConfig.defaults(),
                    approvalHook
            );

            AgentResponse response = runtime.run(AgentRequest.of(
                    "session-toolkit-governance",
                    "user-1",
                    "verify toolkit governance"
            ));

            expect(response.finishReason() == FinishReason.STOP, "toolkit governance should stop");
            expect(response.toolCalls().size() == 6, "toolkit governance should record six tool calls");
            expect(response.toolCalls().get(0).status() == ToolResultStatus.SUCCESS, "file write should succeed");
            expect(response.toolCalls().get(1).status() == ToolResultStatus.PERMISSION_DENIED, "secret path should be denied");
            expect(response.toolCalls().get(2).status() == ToolResultStatus.SUCCESS, "allowed shell command should succeed");
            expect(response.toolCalls().get(3).status() == ToolResultStatus.PERMISSION_DENIED, "dangerous shell command should be denied");
            expect(response.toolCalls().get(4).status() == ToolResultStatus.SUCCESS, "search should succeed");
            expect(response.toolCalls().get(5).status() == ToolResultStatus.SUCCESS, "mcp call should succeed");
            expect(approvalRequests.get() == 2, "shell approval should be requested for both shell calls");
            expect(Files.exists(baseDirectory.resolve("notes.txt")), "file tool should create notes.txt");
            return result(
                    "toolkit governance parity",
                    response,
                    "file, shell, search, MCP, path rules, command rules and approval hooks work together"
            );
        } finally {
            deleteRecursively(baseDirectory);
        }
    }

    private static VerificationResult verifiesSkillsContextProviderProfiles() {
        Path baseDirectory;
        try {
            baseDirectory = Files.createTempDirectory("openharness4j-v13");
            Files.writeString(baseDirectory.resolve("CLAUDE.md"), "Always mention v1.3 readiness.");
            Files.writeString(baseDirectory.resolve("MEMORY.md"), "Persistent note: provider fallback is allowed.");
        } catch (IOException ex) {
            throw new IllegalStateException("failed to prepare v1.3 temp directory", ex);
        }

        try {
            EchoTool echoTool = new EchoTool();
            InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
            toolRegistry.register(echoTool);

            SkillDefinition markdownSkill = loadMarkdownSkill(baseDirectory);
            InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
            skillRegistry.register(markdownSkill);

            AtomicInteger skillLlmCalls = new AtomicInteger();
            LLMAdapter skillLlm = (messages, tools) -> {
                skillLlmCalls.incrementAndGet();
                String prompt = messages.get(messages.size() - 1).content();
                expect(prompt.contains("v1.3 skill topic"), "markdown skill should render input variables");
                return LLMResponse.text("markdown skill completed");
            };
            SkillRunResponse skillResponse = new DefaultSkillExecutor(
                    skillRegistry,
                    skillLlm,
                    toolRegistry,
                    new AllowAllPermissionChecker(),
                    new DefaultAgentTracer(),
                    new DefaultContextManager(),
                    AgentRuntimeConfig.defaults()
            ).run(SkillRunRequest.of(
                    markdownSkill.id(),
                    "session-v13-skill",
                    "user-1",
                    Map.of("topic", "v1.3 skill topic")
            ));

            expect(skillResponse.status() == SkillRunStatus.SUCCESS, "markdown skill should succeed");
            expect(skillResponse.toolCalls().size() == 1, "markdown skill should execute one tool step");
            expect(echoTool.executions() == 1, "markdown skill should call echo once");
            expect(skillLlmCalls.get() == 1, "markdown skill should call LLM once");

            InMemoryMemoryStore memoryStore = new InMemoryMemoryStore();
            MemorySessionManager sessionManager = new MemorySessionManager(memoryStore);
            AtomicInteger contextCalls = new AtomicInteger();
            AtomicReference<List<Message>> secondRunMessages = new AtomicReference<>();
            LLMAdapter contextLlm = (messages, tools) -> {
                if (contextCalls.incrementAndGet() == 2) {
                    secondRunMessages.set(messages);
                    return LLMResponse.text("second context answer");
                }
                return LLMResponse.text("first context answer");
            };
            ContextFileContextManager contextManager = new ContextFileContextManager(
                    new MemoryContextManager(
                            memoryStore,
                            new MemoryWindowPolicy(3, true, new SimpleMemorySummarizer(500))
                    ),
                    baseDirectory,
                    true,
                    true,
                    true,
                    new SimpleMemorySummarizer(500)
            );
            AgentRuntime contextRuntime = new DefaultAgentRuntime(
                    contextLlm,
                    new InMemoryToolRegistry(),
                    new AllowAllPermissionChecker(),
                    new DefaultAgentTracer(),
                    contextManager,
                    AgentRuntimeConfig.defaults()
            );

            contextRuntime.run(AgentRequest.of("session-v13-context", "user-1", "remember azure"));
            AgentResponse contextResponse = contextRuntime.run(
                    AgentRequest.of("session-v13-context", "user-1", "what is the context?")
            );

            expect(contextResponse.finishReason() == FinishReason.STOP, "context file runtime should stop");
            expect(secondRunMessages.get() != null, "second context run should be captured");
            expect(secondRunMessages.get().stream().anyMatch(message -> message.content().contains("v1.3 readiness")),
                    "CLAUDE.md should be injected as context");
            expect(secondRunMessages.get().stream().anyMatch(message -> message.content().contains("remember azure")),
                    "MEMORY.md or session memory should preserve previous conversation");
            List<Message> resumed = sessionManager.resume("session-v13-context");
            expect(resumed.size() == 3, "session memory should auto-compact to configured window");
            expect(resumed.get(0).content().contains("Previous conversation summary"),
                    "auto-compact should preserve a summary message");
            expect(Files.readString(baseDirectory.resolve("MEMORY.md")).contains("what is the context?"),
                    "MEMORY.md should be persisted after completion");

            InMemoryLLMAdapterRegistry providerRegistry = new InMemoryLLMAdapterRegistry();
            providerRegistry.register("primary", (messages, tools) -> {
                throw new LLMAdapterException("primary profile unavailable");
            });
            providerRegistry.register("fallback", (messages, tools) -> LLMResponse.text("provider fallback completed"));
            LLMAdapter selected = new LLMProviderProfileSelector("primary", List.of("primary", "fallback"))
                    .select(providerRegistry)
                    .orElseThrow(() -> new IllegalStateException("provider profile should select adapters"));
            LLMResponse providerResponse = selected.chat(List.of(Message.user("verify provider profile")), List.of());
            expect("provider fallback completed".equals(providerResponse.message().content()),
                    "provider profile selector should return fallback adapter chain");

            return new VerificationResult(
                    "skills context provider profiles",
                    FinishReason.STOP,
                    skillResponse.toolCalls().size(),
                    skillResponse.usage().totalTokens() + contextResponse.usage().totalTokens() + providerResponse.usage().totalTokens(),
                    contextResponse.traceId(),
                    "markdown skills, context files, session compact and provider fallback work together"
            );
        } catch (IOException ex) {
            throw new IllegalStateException("failed to verify v1.3 features", ex);
        } finally {
            deleteRecursively(baseDirectory);
        }
    }

    private static VerificationResult verifiesPersonalAgentTeamRuntime() {
        AtomicReference<String> personalInput = new AtomicReference<>();
        AtomicReference<Map<String, Object>> personalMetadata = new AtomicReference<>();
        long personalTokens;
        try (DefaultPersonalAgentService personalAgent = new DefaultPersonalAgentService(request -> {
            personalInput.set(request.input());
            personalMetadata.set(request.metadata());
            return new AgentResponse(
                    "personal agent completed: " + request.input(),
                    List.of(),
                    new Usage(5, 4, 9),
                    "trace-personal-agent",
                    FinishReason.STOP
            );
        })) {
            PersonalAgentMessage message = new SlackChannelAdapter().toMessage(Map.of(
                    "channel_id", "C-v15",
                    "user_id", "U-v15",
                    "text", "prepare weekly brief"
            ));
            PersonalAgentSubmission submission = personalAgent.submit(message);
            PersonalAgentTaskSnapshot snapshot = awaitPersonalTaskStatus(
                    personalAgent,
                    submission.taskId(),
                    TaskStatus.SUCCEEDED
            );

            expect("slack".equals(submission.channel()), "personal agent should accept Slack channel payloads");
            expect("prepare weekly brief".equals(personalInput.get()), "personal agent should pass channel text to runtime");
            expect("slack".equals(personalMetadata.get().get("channel")), "personal agent should carry channel metadata");
            expect(snapshot.content().contains("weekly brief"), "personal agent task should expose runtime output");
            expect(personalAgent.history("U-v15", "C-v15").size() == 2, "personal agent should record user and assistant history");
            expect(personalAgent.auditEvents().stream().anyMatch(event -> event.action().equals("personal_agent.task.submitted")),
                    "personal agent should audit task submission");
            expect(personalAgent.auditEvents().stream().anyMatch(event -> event.action().equals("personal_agent.task.succeeded")),
                    "personal agent should audit task completion");
            personalTokens = ((Number) snapshot.data().get("totalTokens")).longValue();
        }

        InMemoryTeamAgentRegistry teamRegistry = new InMemoryTeamAgentRegistry();
        teamRegistry.register(new TeamAgentDefinition("planner", "Plan work", request -> new AgentResponse(
                "plan=ready",
                List.of(),
                new Usage(3, 2, 5),
                "trace-team-planner",
                FinishReason.STOP
        )));
        teamRegistry.register(new TeamAgentDefinition("reviewer", "Review work", request -> new AgentResponse(
                "review=approved",
                List.of(),
                Usage.zero(),
                "trace-team-reviewer",
                FinishReason.STOP
        )));

        try (InMemoryTeamRuntime teamRuntime = new InMemoryTeamRuntime(teamRegistry)) {
            TeamAgentSubmission submission = teamRuntime.spawn(TeamAgentRequest.of(
                    "planner",
                    "session-v15-team",
                    "user-1",
                    "plan the release"
            ));
            TeamAgentSnapshot snapshot = awaitTeamTaskStatus(teamRuntime, submission.taskId(), TaskStatus.SUCCEEDED);
            TeamAgentArchive archive = teamRuntime.archive(submission.taskId()).orElseThrow();

            expect(teamRegistry.list().size() == 2, "team registry should manage multiple long-lived sub agents");
            expect("planner".equals(snapshot.agentId()), "team runtime should preserve spawned agent id");
            expect("plan=ready".equals(snapshot.content()), "team runtime should expose sub agent output");
            expect(archive.snapshot().taskId().equals(submission.taskId()), "team runtime should archive completed task result");
            expect(teamRuntime.auditStore().list().stream().anyMatch(event -> event.action().equals("team_runtime.agent.spawned")),
                    "team runtime should audit spawning");
            expect(teamRuntime.auditStore().list().stream().anyMatch(event -> event.action().equals("team_runtime.agent.archived")),
                    "team runtime should audit archiving");

            return new VerificationResult(
                    "personal agent team runtime",
                    FinishReason.STOP,
                    0,
                    personalTokens + ((Number) snapshot.data().get("totalTokens")).longValue(),
                    archive.archiveId(),
                    "Slack personal-agent submission plus team spawn, query, cancel-ready lifecycle and archive work together"
            );
        }
    }

    private static SkillDefinition loadMarkdownSkill(Path baseDirectory) throws IOException {
        Path skillFile = baseDirectory.resolve("v13-skill.md");
        Files.writeString(skillFile, """
                ---
                name: V1.3 Markdown Skill
                version: 1.3.0
                requiredTools:
                  - echo
                workflow:
                  - name: echo_markdown
                    type: tool
                    tool: echo
                    args:
                      text: "{{topic}}"
                  - name: summarize
                    type: llm
                    prompt: "Summarize {{steps.echo_markdown.output}} for {{topic}}."
                ---
                Use the topic to prepare a verification response.
                """);
        return new MarkdownSkillLoader().load(skillFile);
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

    private static TaskHandler taskHandler(String type, ThrowingTaskHandler handler) {
        return new TaskHandler() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public TaskResult handle(TaskContext context) throws Exception {
                return handler.handle(context);
            }
        };
    }

    private static TaskSnapshot awaitTaskStatus(InMemoryTaskEngine taskEngine, String taskId, TaskStatus status) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        TaskSnapshot latest = taskEngine.get(taskId).orElseThrow();
        while (System.nanoTime() < deadline) {
            latest = taskEngine.get(taskId).orElseThrow();
            if (latest.status() == status) {
                return latest;
            }
            sleep(10);
        }
        throw new IllegalStateException("expected " + status + " but was " + latest.status());
    }

    private static PersonalAgentTaskSnapshot awaitPersonalTaskStatus(
            DefaultPersonalAgentService personalAgent,
            String taskId,
            TaskStatus status
    ) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        PersonalAgentTaskSnapshot latest = personalAgent.get(taskId).orElseThrow();
        while (System.nanoTime() < deadline) {
            latest = personalAgent.get(taskId).orElseThrow();
            if (latest.status() == status) {
                return latest;
            }
            sleep(10);
        }
        throw new IllegalStateException("expected " + status + " but was " + latest.status());
    }

    private static TeamAgentSnapshot awaitTeamTaskStatus(TeamRuntime teamRuntime, String taskId, TaskStatus status) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        TeamAgentSnapshot latest = teamRuntime.get(taskId).orElseThrow();
        while (System.nanoTime() < deadline) {
            latest = teamRuntime.get(taskId).orElseThrow();
            if (latest.status() == status) {
                return latest;
            }
            sleep(10);
        }
        throw new IllegalStateException("expected " + status + " but was " + latest.status());
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", ex);
        }
    }

    private static Tool slowTool(String name, AtomicInteger active, AtomicInteger maxActive) {
        return new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "Slow runtime parity tool.";
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
                return ToolResult.success(name + " completed");
            }
        };
    }

    private static void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                    // best-effort cleanup for verification temp files
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup for verification temp files
        }
    }

    @FunctionalInterface
    private interface ThrowingTaskHandler {
        TaskResult handle(TaskContext context) throws Exception;
    }
}
