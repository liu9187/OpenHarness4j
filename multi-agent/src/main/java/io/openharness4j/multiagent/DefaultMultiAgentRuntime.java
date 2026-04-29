package io.openharness4j.multiagent;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.ToolCallRecord;
import io.openharness4j.api.Usage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultMultiAgentRuntime implements MultiAgentRuntime {

    private final SubAgentRegistry subAgentRegistry;
    private final PlanningAgent planningAgent;
    private final ConflictResolver conflictResolver;
    private final MultiAgentAggregator aggregator;

    public DefaultMultiAgentRuntime(SubAgentRegistry subAgentRegistry) {
        this(
                subAgentRegistry,
                new DefaultPlanningAgent(),
                new KeyValueConflictResolver(),
                new DefaultMultiAgentAggregator()
        );
    }

    public DefaultMultiAgentRuntime(
            SubAgentRegistry subAgentRegistry,
            PlanningAgent planningAgent,
            ConflictResolver conflictResolver,
            MultiAgentAggregator aggregator
    ) {
        this.subAgentRegistry = Objects.requireNonNull(subAgentRegistry, "subAgentRegistry must not be null");
        this.planningAgent = planningAgent == null ? new DefaultPlanningAgent() : planningAgent;
        this.conflictResolver = conflictResolver == null ? new KeyValueConflictResolver() : conflictResolver;
        this.aggregator = aggregator == null ? new DefaultMultiAgentAggregator() : aggregator;
    }

    @Override
    public MultiAgentResponse run(MultiAgentRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<SubAgentDefinition> agents = subAgentRegistry.list();
        List<AgentTask> tasks;
        try {
            tasks = planningAgent.plan(request, agents);
        } catch (RuntimeException ex) {
            return MultiAgentResponse.failed("PLANNING_FAILED", safeMessage(ex));
        }
        if (tasks == null || tasks.isEmpty()) {
            return MultiAgentResponse.failed("NO_AGENT_TASKS", "planning agent returned no tasks");
        }

        List<AgentTaskResult> results = new ArrayList<>();
        List<ToolCallRecord> toolCalls = new ArrayList<>();
        Usage usage = Usage.zero();
        for (AgentTask task : tasks) {
            AgentTaskResult result = runTask(request, task);
            results.add(result);
            toolCalls.addAll(result.toolCalls());
            usage = usage.plus(result.usage());
        }

        List<AgentConflict> conflicts = conflictResolver.detect(results);
        MultiAgentStatus status = status(results, conflicts);
        String output = aggregator.aggregate(request, tasks, results, conflicts);
        String errorCode = status == MultiAgentStatus.FAILED ? firstErrorCode(results) : "";
        String errorMessage = status == MultiAgentStatus.FAILED ? firstErrorMessage(results) : "";
        return new MultiAgentResponse(status, output, tasks, results, conflicts, toolCalls, usage, errorCode, errorMessage);
    }

    private AgentTaskResult runTask(MultiAgentRequest request, AgentTask task) {
        return subAgentRegistry.get(task.agentId())
                .map(agent -> execute(request, task, agent))
                .orElseGet(() -> AgentTaskResult.failed(
                        task,
                        "SUB_AGENT_NOT_FOUND",
                        "sub agent not found: " + task.agentId()
                ));
    }

    private static AgentTaskResult execute(MultiAgentRequest request, AgentTask task, SubAgentDefinition agent) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
            metadata.putAll(task.metadata());
            metadata.put("parentSessionId", request.sessionId());
            metadata.put("subAgentId", agent.agentId());
            metadata.put("subAgentRole", agent.role());
            metadata.put("agentTaskId", task.taskId());
            AgentResponse response = agent.runtime().run(new AgentRequest(
                    request.sessionId() + ":" + agent.agentId() + ":" + task.taskId(),
                    request.userId(),
                    task.instruction(),
                    metadata
            ));
            AgentTaskStatus status = response.finishReason() == FinishReason.STOP
                    ? AgentTaskStatus.SUCCEEDED
                    : AgentTaskStatus.FAILED;
            return new AgentTaskResult(
                    task.taskId(),
                    task.agentId(),
                    task.instruction(),
                    status,
                    response.content(),
                    response.traceId(),
                    response.finishReason(),
                    response.toolCalls(),
                    response.usage(),
                    status == AgentTaskStatus.FAILED ? "SUB_AGENT_FAILED" : "",
                    status == AgentTaskStatus.FAILED ? "sub agent finished with " + response.finishReason() : ""
            );
        } catch (RuntimeException ex) {
            return AgentTaskResult.failed(task, "SUB_AGENT_EXECUTION_FAILED", safeMessage(ex));
        }
    }

    private static MultiAgentStatus status(List<AgentTaskResult> results, List<AgentConflict> conflicts) {
        if (!conflicts.isEmpty()) {
            return MultiAgentStatus.CONFLICT;
        }
        if (results.stream().anyMatch(result -> result.status() == AgentTaskStatus.FAILED)) {
            return MultiAgentStatus.FAILED;
        }
        return MultiAgentStatus.SUCCESS;
    }

    private static String firstErrorCode(List<AgentTaskResult> results) {
        return results.stream()
                .filter(result -> !result.errorCode().isBlank())
                .map(AgentTaskResult::errorCode)
                .findFirst()
                .orElse("");
    }

    private static String firstErrorMessage(List<AgentTaskResult> results) {
        return results.stream()
                .filter(result -> !result.errorMessage().isBlank())
                .map(AgentTaskResult::errorMessage)
                .findFirst()
                .orElse("");
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
