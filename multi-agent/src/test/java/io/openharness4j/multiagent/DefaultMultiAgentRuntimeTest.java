package io.openharness4j.multiagent;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.Usage;
import io.openharness4j.runtime.AgentRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMultiAgentRuntimeTest {

    @Test
    void planningAgentSplitsWorkAndSubAgentsExecuteIndependentTasks() {
        InMemorySubAgentRegistry registry = new InMemorySubAgentRegistry();
        AtomicReference<String> researcherInput = new AtomicReference<>();
        AtomicReference<String> reviewerInput = new AtomicReference<>();
        registry.register(new SubAgentDefinition("researcher", "Research", runtime("facts=ready", researcherInput)));
        registry.register(new SubAgentDefinition("reviewer", "Review", runtime("review=approved", reviewerInput)));
        AtomicInteger planningCalls = new AtomicInteger();
        PlanningAgent planner = (request, agents) -> {
            planningCalls.incrementAndGet();
            return List.of(
                    new AgentTask("task_research", "researcher", "collect facts", Map.of()),
                    new AgentTask("task_review", "reviewer", "review facts", Map.of())
            );
        };

        MultiAgentResponse response = new DefaultMultiAgentRuntime(
                registry,
                planner,
                new KeyValueConflictResolver(),
                new DefaultMultiAgentAggregator()
        ).run(MultiAgentRequest.of("session-multi", "user-1", "prepare report"));

        assertEquals(MultiAgentStatus.SUCCESS, response.status());
        assertEquals(1, planningCalls.get());
        assertEquals(2, response.tasks().size());
        assertEquals(2, response.results().size());
        assertEquals("collect facts", researcherInput.get());
        assertEquals("review facts", reviewerInput.get());
        assertTrue(response.output().contains("researcher"));
        assertTrue(response.output().contains("reviewer"));
    }

    @Test
    void detectsConflictsAcrossSubAgentResults() {
        InMemorySubAgentRegistry registry = new InMemorySubAgentRegistry();
        registry.register(new SubAgentDefinition("risk", "Risk", runtime("risk=low", null)));
        registry.register(new SubAgentDefinition("finance", "Finance", runtime("risk=high", null)));
        PlanningAgent planner = (request, agents) -> List.of(
                AgentTask.of("risk", "assess risk"),
                AgentTask.of("finance", "assess finance risk")
        );

        MultiAgentResponse response = new DefaultMultiAgentRuntime(registry, planner, null, null)
                .run(MultiAgentRequest.of("session-conflict", "user-1", "assess"));

        assertEquals(MultiAgentStatus.CONFLICT, response.status());
        assertEquals(1, response.conflicts().size());
        assertEquals("risk", response.conflicts().get(0).key());
        assertTrue(response.output().contains("Conflicts detected"));
    }

    @Test
    void returnsFailedWhenPlannedSubAgentIsMissing() {
        InMemorySubAgentRegistry registry = new InMemorySubAgentRegistry();
        PlanningAgent planner = (request, agents) -> List.of(AgentTask.of("missing", "do work"));

        MultiAgentResponse response = new DefaultMultiAgentRuntime(registry, planner, null, null)
                .run(MultiAgentRequest.of("session-missing", "user-1", "work"));

        assertEquals(MultiAgentStatus.FAILED, response.status());
        assertEquals("SUB_AGENT_NOT_FOUND", response.errorCode());
        assertEquals(1, response.results().size());
        assertEquals(AgentTaskStatus.FAILED, response.results().get(0).status());
    }

    @Test
    void defaultPlanningAgentParsesMetadataTasks() {
        InMemorySubAgentRegistry registry = new InMemorySubAgentRegistry();
        registry.register(new SubAgentDefinition("alpha", "Alpha", runtime("alpha=done", null)));
        registry.register(new SubAgentDefinition("beta", "Beta", runtime("beta=done", null)));
        Map<String, Object> metadata = Map.of(
                DefaultPlanningAgent.TASKS_METADATA_KEY,
                List.of(
                        Map.of("taskId", "alpha_task", "agentId", "alpha", "instruction", "alpha work"),
                        Map.of("taskId", "beta_task", "agentId", "beta", "instruction", "beta work")
                )
        );

        MultiAgentResponse response = new DefaultMultiAgentRuntime(registry)
                .run(new MultiAgentRequest("session-default", "user-1", "ignored", metadata));

        assertEquals(MultiAgentStatus.SUCCESS, response.status());
        assertEquals(2, response.tasks().size());
        assertEquals("alpha_task", response.tasks().get(0).taskId());
        assertEquals("beta_task", response.tasks().get(1).taskId());
    }

    @Test
    void rejectsDuplicateSubAgents() {
        InMemorySubAgentRegistry registry = new InMemorySubAgentRegistry();
        registry.register(new SubAgentDefinition("agent", "First", runtime("ok", null)));

        assertThrows(IllegalArgumentException.class, () -> registry.register(new SubAgentDefinition("agent", "Second", runtime("ok", null))));
    }

    private static AgentRuntime runtime(String content, AtomicReference<String> capturedInput) {
        return request -> {
            if (capturedInput != null) {
                capturedInput.set(request.input());
            }
            return response(content);
        };
    }

    private static AgentResponse response(String content) {
        return new AgentResponse(content, List.of(), Usage.zero(), "trace-test", FinishReason.STOP);
    }
}
