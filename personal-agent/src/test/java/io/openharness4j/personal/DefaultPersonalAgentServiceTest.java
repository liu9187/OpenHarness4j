package io.openharness4j.personal;

import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.Usage;
import io.openharness4j.personal.channel.SlackChannelAdapter;
import io.openharness4j.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPersonalAgentServiceTest {

    @Test
    void submitsSlackMessageToBackgroundRuntimeAndRecordsWorkspaceHistoryAndAudit() {
        AtomicReference<String> capturedInput = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedMetadata = new AtomicReference<>();

        try (DefaultPersonalAgentService service = new DefaultPersonalAgentService(request -> {
            capturedInput.set(request.input());
            capturedMetadata.set(request.metadata());
            return new AgentResponse(
                    "answer for " + request.input(),
                    List.of(),
                    new Usage(3, 4, 7),
                    "trace-personal",
                    FinishReason.STOP
            );
        })) {
            PersonalAgentMessage message = new SlackChannelAdapter().toMessage(Map.of(
                    "channel_id", "C123",
                    "user_id", "U123",
                    "text", "summarize my day",
                    "timeoutMillis", 1000
            ));

            PersonalAgentSubmission submission = service.submit(message);
            PersonalAgentTaskSnapshot snapshot = awaitStatus(service, submission.taskId(), TaskStatus.SUCCEEDED);

            assertEquals("slack", submission.channel());
            assertEquals("C123", submission.conversationId());
            assertEquals("answer for summarize my day", snapshot.content());
            assertEquals("summarize my day", capturedInput.get());
            assertEquals("slack", capturedMetadata.get().get("channel"));
            assertFalse(submission.workspaceId().isBlank());
            assertEquals(submission.workspaceId(), service.workspace("U123").workspaceId());

            List<PersonalAgentHistoryEntry> history = service.history("U123", "C123");
            assertEquals(2, history.size());
            assertEquals("user", history.get(0).role());
            assertEquals("assistant", history.get(1).role());
            assertTrue(service.auditEvents().stream()
                    .anyMatch(event -> event.action().equals("personal_agent.task.submitted")));
            assertTrue(service.auditEvents().stream()
                    .anyMatch(event -> event.action().equals("personal_agent.task.succeeded")));
        }
    }

    private static PersonalAgentTaskSnapshot awaitStatus(
            PersonalAgentService service,
            String taskId,
            TaskStatus status
    ) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        PersonalAgentTaskSnapshot latest = service.get(taskId).orElseThrow();
        while (System.nanoTime() < deadline) {
            latest = service.get(taskId).orElseThrow();
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
            throw new AssertionError("interrupted while waiting", ex);
        }
    }
}
