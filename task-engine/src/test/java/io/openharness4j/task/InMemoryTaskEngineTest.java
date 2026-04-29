package io.openharness4j.task;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTaskEngineTest {

    @Test
    void runsAsyncTaskAndAllowsStatusQuery() throws Exception {
        InMemoryTaskRegistry registry = new InMemoryTaskRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        registry.register(handler("report", context -> {
            started.countDown();
            release.await(1, TimeUnit.SECONDS);
            return TaskResult.success("report ready", Map.of("input", context.input().get("name")));
        }));

        try (InMemoryTaskEngine engine = new InMemoryTaskEngine(registry, 0, 2)) {
            TaskSubmission submission = engine.submit(TaskRequest.of("report", Map.of("name", "daily")));

            assertEquals(TaskStatus.PENDING, submission.status());
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertEquals(TaskStatus.RUNNING, engine.get(submission.taskId()).orElseThrow().status());

            release.countDown();
            TaskSnapshot completed = awaitStatus(engine, submission.taskId(), TaskStatus.SUCCEEDED);

            assertEquals("report ready", completed.content());
            assertEquals("daily", completed.data().get("input"));
            assertFalse(completed.taskId().isBlank());
            assertEquals("report", completed.type());
        }
    }

    @Test
    void cancelsRunningTask() throws Exception {
        InMemoryTaskRegistry registry = new InMemoryTaskRegistry();
        CountDownLatch started = new CountDownLatch(1);
        registry.register(handler("slow", context -> {
            started.countDown();
            while (!context.cancellationRequested()) {
                Thread.sleep(10);
            }
            return TaskResult.success("should not win");
        }));

        try (InMemoryTaskEngine engine = new InMemoryTaskEngine(registry, 0, 1)) {
            TaskSubmission submission = engine.submit(TaskRequest.of("slow", Map.of()));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(engine.cancel(submission.taskId()));
            TaskSnapshot cancelled = awaitStatus(engine, submission.taskId(), TaskStatus.CANCELLED);

            assertEquals("TASK_CANCELLED", cancelled.errorCode());
            assertFalse(engine.cancel(submission.taskId()));
        }
    }

    @Test
    void marksTaskTimedOutAndInterruptsExecution() throws Exception {
        InMemoryTaskRegistry registry = new InMemoryTaskRegistry();
        CountDownLatch started = new CountDownLatch(1);
        registry.register(handler("timeout", context -> {
            started.countDown();
            Thread.sleep(1_000);
            return TaskResult.success("too late");
        }));

        try (InMemoryTaskEngine engine = new InMemoryTaskEngine(registry, 0, 1)) {
            TaskSubmission submission = engine.submit(TaskRequest.withTimeout("timeout", Map.of(), 50));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            TaskSnapshot timedOut = awaitStatus(engine, submission.taskId(), TaskStatus.TIMED_OUT);

            assertEquals("TASK_TIMEOUT", timedOut.errorCode());
            assertEquals(50, timedOut.timeoutMillis());
        }
    }

    @Test
    void failsImmediatelyWhenHandlerIsMissing() {
        try (InMemoryTaskEngine engine = new InMemoryTaskEngine(new InMemoryTaskRegistry(), 0, 1)) {
            TaskSubmission submission = engine.submit(TaskRequest.of("missing", Map.of()));
            TaskSnapshot snapshot = engine.get(submission.taskId()).orElseThrow();

            assertEquals(TaskStatus.FAILED, submission.status());
            assertEquals(TaskStatus.FAILED, snapshot.status());
            assertEquals("TASK_HANDLER_NOT_FOUND", snapshot.errorCode());
        }
    }

    @Test
    void rejectsDuplicateHandlers() {
        InMemoryTaskRegistry registry = new InMemoryTaskRegistry();
        registry.register(handler("echo", context -> TaskResult.success("first")));

        assertThrows(IllegalArgumentException.class, () -> registry.register(handler("echo", context -> TaskResult.success("second"))));
    }

    private static TaskSnapshot awaitStatus(InMemoryTaskEngine engine, String taskId, TaskStatus status) throws InterruptedException {
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

    private static TaskHandler handler(String type, ThrowingTaskHandler handler) {
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

    @FunctionalInterface
    private interface ThrowingTaskHandler {
        TaskResult handle(TaskContext context) throws Exception;
    }
}
