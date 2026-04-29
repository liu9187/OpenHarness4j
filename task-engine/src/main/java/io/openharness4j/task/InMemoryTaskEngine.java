package io.openharness4j.task;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InMemoryTaskEngine implements TaskEngine, AutoCloseable {

    public static final long DEFAULT_TIMEOUT_MILLIS = 0;

    private final TaskRegistry taskRegistry;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduler;
    private final long defaultTimeoutMillis;
    private final Map<String, MutableTask> tasks = new ConcurrentHashMap<>();

    public InMemoryTaskEngine(TaskRegistry taskRegistry) {
        this(taskRegistry, Executors.newCachedThreadPool(), Executors.newSingleThreadScheduledExecutor(), DEFAULT_TIMEOUT_MILLIS);
    }

    public InMemoryTaskEngine(TaskRegistry taskRegistry, long defaultTimeoutMillis, int poolSize) {
        this(
                taskRegistry,
                Executors.newFixedThreadPool(Math.max(1, poolSize)),
                Executors.newSingleThreadScheduledExecutor(),
                defaultTimeoutMillis
        );
    }

    public InMemoryTaskEngine(
            TaskRegistry taskRegistry,
            ExecutorService executorService,
            ScheduledExecutorService scheduler,
            long defaultTimeoutMillis
    ) {
        this.taskRegistry = Objects.requireNonNull(taskRegistry, "taskRegistry must not be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.defaultTimeoutMillis = Math.max(0, defaultTimeoutMillis);
    }

    @Override
    public TaskSubmission submit(TaskRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String taskId = "task_" + UUID.randomUUID();
        long timeoutMillis = request.timeoutMillis() > 0 ? request.timeoutMillis() : defaultTimeoutMillis;
        MutableTask task = MutableTask.pending(taskId, request, timeoutMillis);
        tasks.put(taskId, task);

        Optional<TaskHandler> handler = taskRegistry.get(request.type());
        if (handler.isEmpty()) {
            task.fail("TASK_HANDLER_NOT_FOUND", "task handler not found: " + request.type());
            return new TaskSubmission(taskId, TaskStatus.FAILED);
        }

        Future<?> future = executorService.submit(() -> runTask(task, handler.get()));
        task.future(future);
        if (timeoutMillis > 0) {
            scheduler.schedule(() -> timeout(taskId), timeoutMillis, TimeUnit.MILLISECONDS);
        }
        return new TaskSubmission(taskId, TaskStatus.PENDING);
    }

    @Override
    public Optional<TaskSnapshot> get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(taskId)).map(MutableTask::snapshot);
    }

    @Override
    public boolean cancel(String taskId) {
        MutableTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        if (!task.cancel()) {
            return false;
        }
        Future<?> future = task.future();
        if (future != null) {
            future.cancel(true);
        }
        return true;
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        scheduler.shutdownNow();
    }

    private void runTask(MutableTask task, TaskHandler handler) {
        if (!task.start()) {
            return;
        }
        try {
            TaskContext context = new TaskContext(
                    task.taskId(),
                    task.type(),
                    task.input(),
                    task.metadata(),
                    task.deadline(),
                    task::cancellationRequested
            );
            TaskResult result = handler.handle(context);
            if (task.cancellationRequested()) {
                task.cancel();
                return;
            }
            if (result == null) {
                task.fail("NULL_TASK_RESULT", "task handler returned null result");
                return;
            }
            if (result.success()) {
                task.succeed(result);
            } else {
                task.fail(result.errorCode(), result.errorMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            task.cancel();
        } catch (Exception ex) {
            if (task.cancellationRequested()) {
                task.cancel();
            } else {
                task.fail("TASK_EXECUTION_FAILED", safeMessage(ex));
            }
        }
    }

    private void timeout(String taskId) {
        MutableTask task = tasks.get(taskId);
        if (task == null || !task.timeout()) {
            return;
        }
        Future<?> future = task.future();
        if (future != null) {
            future.cancel(true);
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static final class MutableTask {
        private final String taskId;
        private final String type;
        private final Map<String, Object> input;
        private final Map<String, Object> metadata;
        private final Instant submittedAt;
        private final long timeoutMillis;
        private final Instant deadline;
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();
        private TaskStatus status = TaskStatus.PENDING;
        private String content = "";
        private Map<String, Object> data = Map.of();
        private String errorCode = "";
        private String errorMessage = "";
        private Instant startedAt;
        private Instant completedAt;
        private Future<?> future;

        private MutableTask(
                String taskId,
                String type,
                Map<String, Object> input,
                Map<String, Object> metadata,
                Instant submittedAt,
                long timeoutMillis
        ) {
            this.taskId = taskId;
            this.type = type;
            this.input = Map.copyOf(input);
            this.metadata = Map.copyOf(metadata);
            this.submittedAt = submittedAt;
            this.timeoutMillis = timeoutMillis;
            this.deadline = timeoutMillis > 0 ? submittedAt.plusMillis(timeoutMillis) : null;
        }

        static MutableTask pending(String taskId, TaskRequest request, long timeoutMillis) {
            return new MutableTask(taskId, request.type(), request.input(), request.metadata(), Instant.now(), timeoutMillis);
        }

        synchronized boolean start() {
            if (status.terminal()) {
                return false;
            }
            status = TaskStatus.RUNNING;
            startedAt = Instant.now();
            return true;
        }

        synchronized void succeed(TaskResult result) {
            if (status.terminal()) {
                return;
            }
            status = TaskStatus.SUCCEEDED;
            content = result.content();
            data = result.data();
            completedAt = Instant.now();
        }

        synchronized void fail(String errorCode, String errorMessage) {
            if (status.terminal()) {
                return;
            }
            status = TaskStatus.FAILED;
            this.errorCode = errorCode == null || errorCode.isBlank() ? "TASK_FAILED" : errorCode;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            completedAt = Instant.now();
        }

        synchronized boolean cancel() {
            cancellationRequested.set(true);
            if (status.terminal()) {
                return false;
            }
            status = TaskStatus.CANCELLED;
            errorCode = "TASK_CANCELLED";
            errorMessage = "task was cancelled";
            completedAt = Instant.now();
            return true;
        }

        synchronized boolean timeout() {
            cancellationRequested.set(true);
            if (status.terminal()) {
                return false;
            }
            status = TaskStatus.TIMED_OUT;
            errorCode = "TASK_TIMEOUT";
            errorMessage = "task timed out after " + timeoutMillis + " ms";
            completedAt = Instant.now();
            return true;
        }

        synchronized TaskSnapshot snapshot() {
            return new TaskSnapshot(
                    taskId,
                    type,
                    status,
                    content,
                    new LinkedHashMap<>(data),
                    errorCode,
                    errorMessage,
                    submittedAt,
                    startedAt,
                    completedAt,
                    timeoutMillis,
                    metadata
            );
        }

        synchronized void future(Future<?> future) {
            this.future = future;
        }

        synchronized Future<?> future() {
            return future;
        }

        String taskId() {
            return taskId;
        }

        String type() {
            return type;
        }

        Map<String, Object> input() {
            return input;
        }

        Map<String, Object> metadata() {
            return metadata;
        }

        Instant deadline() {
            return deadline;
        }

        boolean cancellationRequested() {
            return cancellationRequested.get();
        }
    }
}
