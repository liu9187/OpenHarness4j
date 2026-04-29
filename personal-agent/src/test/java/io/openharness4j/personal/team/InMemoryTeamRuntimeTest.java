package io.openharness4j.personal.team;

import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.Usage;
import io.openharness4j.personal.InMemoryPersonalAgentAuditStore;
import io.openharness4j.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTeamRuntimeTest {

    @Test
    void spawnsQueriesAndArchivesLongLivedTeamAgent() {
        InMemoryTeamAgentRegistry registry = new InMemoryTeamAgentRegistry();
        registry.register(new TeamAgentDefinition("researcher", "Research", request -> new AgentResponse(
                "research ready: " + request.input(),
                List.of(),
                new Usage(1, 2, 3),
                "trace-research",
                FinishReason.STOP
        )));
        registry.register(new TeamAgentDefinition("reviewer", "Review", request -> new AgentResponse(
                "review ready",
                List.of(),
                Usage.zero(),
                "trace-review",
                FinishReason.STOP
        )));
        InMemoryPersonalAgentAuditStore auditStore = new InMemoryPersonalAgentAuditStore();

        try (InMemoryTeamRuntime runtime = new InMemoryTeamRuntime(registry, auditStore, 0, 2)) {
            TeamAgentSubmission submission = runtime.spawn(TeamAgentRequest.of(
                    "researcher",
                    "session-team",
                    "user-1",
                    "collect facts"
            ));

            TeamAgentSnapshot snapshot = awaitStatus(runtime, submission.taskId(), TaskStatus.SUCCEEDED);
            TeamAgentArchive archive = runtime.archive(submission.taskId()).orElseThrow();

            assertEquals(2, registry.list().size());
            assertEquals("researcher", snapshot.agentId());
            assertEquals("Research", snapshot.role());
            assertEquals("research ready: collect facts", snapshot.content());
            assertEquals(snapshot.taskId(), archive.snapshot().taskId());
            assertTrue(runtime.archived(submission.taskId()).isPresent());
            assertTrue(auditStore.list().stream()
                    .anyMatch(event -> event.action().equals("team_runtime.agent.spawned")));
            assertTrue(auditStore.list().stream()
                    .anyMatch(event -> event.action().equals("team_runtime.agent.archived")));
        }
    }

    @Test
    void cancelsRunningTeamAgent() throws Exception {
        InMemoryTeamAgentRegistry registry = new InMemoryTeamAgentRegistry();
        CountDownLatch started = new CountDownLatch(1);
        registry.register(new TeamAgentDefinition("slow", "Slow worker", request -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                sleep(10);
            }
            return new AgentResponse("interrupted", List.of(), Usage.zero(), "trace-slow", FinishReason.STOP);
        }));

        try (InMemoryTeamRuntime runtime = new InMemoryTeamRuntime(registry)) {
            TeamAgentSubmission submission = runtime.spawn(TeamAgentRequest.of(
                    "slow",
                    "session-team",
                    "user-1",
                    "wait"
            ));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(runtime.cancel(submission.taskId()));
            TeamAgentSnapshot snapshot = awaitStatus(runtime, submission.taskId(), TaskStatus.CANCELLED);

            assertEquals("slow", snapshot.agentId());
            assertFalse(runtime.archive("missing").isPresent());
        }
    }

    private static TeamAgentSnapshot awaitStatus(TeamRuntime runtime, String taskId, TaskStatus status) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        TeamAgentSnapshot latest = runtime.get(taskId).orElseThrow();
        while (System.nanoTime() < deadline) {
            latest = runtime.get(taskId).orElseThrow();
            if (latest.status() == status) {
                return latest;
            }
            sleep(10);
        }
        throw new AssertionError("expected " + status + " but was " + latest.status());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
