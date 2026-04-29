package io.openharness4j.personal;

import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.task.InMemoryTaskEngine;
import io.openharness4j.task.InMemoryTaskRegistry;
import io.openharness4j.task.TaskEngine;
import io.openharness4j.task.TaskRequest;
import io.openharness4j.task.TaskSnapshot;
import io.openharness4j.task.TaskStatus;
import io.openharness4j.task.TaskSubmission;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPersonalAgentService implements PersonalAgentService, AutoCloseable {

    private final TaskEngine taskEngine;
    private final PersonalWorkspaceStore workspaceStore;
    private final PersonalAgentHistoryStore historyStore;
    private final PersonalAgentTaskStore taskStore;
    private final PersonalAgentAuditStore auditStore;
    private final String taskType;
    private final AutoCloseable ownedCloseable;
    private final Set<String> observedTerminalTasks = ConcurrentHashMap.newKeySet();

    public DefaultPersonalAgentService(AgentRuntime runtime) {
        this(defaultBackend(runtime));
    }

    public DefaultPersonalAgentService(TaskEngine taskEngine) {
        this(
                taskEngine,
                new InMemoryPersonalWorkspaceStore(),
                new InMemoryPersonalAgentHistoryStore(),
                new InMemoryPersonalAgentTaskStore(),
                new InMemoryPersonalAgentAuditStore(),
                PersonalAgentTaskHandler.DEFAULT_TYPE,
                null
        );
    }

    public DefaultPersonalAgentService(
            TaskEngine taskEngine,
            PersonalWorkspaceStore workspaceStore,
            PersonalAgentHistoryStore historyStore,
            PersonalAgentTaskStore taskStore,
            PersonalAgentAuditStore auditStore,
            String taskType
    ) {
        this(taskEngine, workspaceStore, historyStore, taskStore, auditStore, taskType, null);
    }

    private DefaultPersonalAgentService(TaskBackend backend) {
        this(
                backend.taskEngine(),
                new InMemoryPersonalWorkspaceStore(),
                new InMemoryPersonalAgentHistoryStore(),
                new InMemoryPersonalAgentTaskStore(),
                new InMemoryPersonalAgentAuditStore(),
                PersonalAgentTaskHandler.DEFAULT_TYPE,
                backend.closeable()
        );
    }

    private DefaultPersonalAgentService(
            TaskEngine taskEngine,
            PersonalWorkspaceStore workspaceStore,
            PersonalAgentHistoryStore historyStore,
            PersonalAgentTaskStore taskStore,
            PersonalAgentAuditStore auditStore,
            String taskType,
            AutoCloseable ownedCloseable
    ) {
        this.taskEngine = Objects.requireNonNull(taskEngine, "taskEngine must not be null");
        this.workspaceStore = Objects.requireNonNull(workspaceStore, "workspaceStore must not be null");
        this.historyStore = Objects.requireNonNull(historyStore, "historyStore must not be null");
        this.taskStore = Objects.requireNonNull(taskStore, "taskStore must not be null");
        this.auditStore = Objects.requireNonNull(auditStore, "auditStore must not be null");
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType must not be blank");
        }
        this.taskType = taskType;
        this.ownedCloseable = ownedCloseable;
    }

    @Override
    public PersonalAgentSubmission submit(PersonalAgentMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        PersonalWorkspace workspace = workspaceStore.getOrCreate(message.userId());
        historyStore.append(PersonalAgentHistoryEntry.of(
                message.userId(),
                message.conversationId(),
                message.channel(),
                "user",
                message.text(),
                message.metadata()
        ));

        Map<String, Object> metadata = new LinkedHashMap<>(message.metadata());
        metadata.put("channel", message.channel());
        metadata.put("conversationId", message.conversationId());
        metadata.put("userId", message.userId());
        metadata.put("workspaceId", workspace.workspaceId());

        Map<String, Object> input = Map.of(
                PersonalAgentTaskHandler.INPUT_SESSION_ID, message.conversationId(),
                PersonalAgentTaskHandler.INPUT_USER_ID, message.userId(),
                PersonalAgentTaskHandler.INPUT_TEXT, message.text()
        );
        TaskSubmission submission = taskEngine.submit(new TaskRequest(taskType, input, metadata, timeoutMillis(message.metadata())));
        taskStore.save(new PersonalAgentTaskRecord(
                submission.taskId(),
                message.userId(),
                workspace.workspaceId(),
                message.conversationId(),
                message.channel(),
                metadata,
                Instant.now()
        ));
        auditStore.append(PersonalAgentAuditEvent.of(
                message.userId(),
                "personal_agent.task.submitted",
                submission.taskId(),
                Map.of("channel", message.channel(), "conversationId", message.conversationId())
        ));
        return new PersonalAgentSubmission(
                submission.taskId(),
                submission.status(),
                workspace.workspaceId(),
                message.conversationId(),
                message.channel()
        );
    }

    @Override
    public Optional<PersonalAgentTaskSnapshot> get(String taskId) {
        Optional<PersonalAgentTaskRecord> record = taskStore.get(taskId);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        Optional<TaskSnapshot> snapshot = taskEngine.get(taskId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        observeTerminal(snapshot.get(), record.get());
        return Optional.of(PersonalAgentTaskSnapshot.from(snapshot.get(), record.get()));
    }

    @Override
    public boolean cancel(String taskId) {
        boolean cancelled = taskEngine.cancel(taskId);
        if (cancelled) {
            taskStore.get(taskId).ifPresent(record -> auditStore.append(PersonalAgentAuditEvent.of(
                    record.userId(),
                    "personal_agent.task.cancelled",
                    taskId,
                    Map.of("conversationId", record.conversationId())
            )));
        }
        return cancelled;
    }

    @Override
    public PersonalWorkspace workspace(String userId) {
        return workspaceStore.getOrCreate(userId);
    }

    @Override
    public List<PersonalAgentHistoryEntry> history(String userId, String conversationId) {
        return historyStore.listByConversation(userId, conversationId);
    }

    @Override
    public List<PersonalAgentAuditEvent> auditEvents() {
        return auditStore.list();
    }

    @Override
    public void close() {
        if (ownedCloseable == null) {
            return;
        }
        try {
            ownedCloseable.close();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to close owned personal agent resources", ex);
        }
    }

    private void observeTerminal(TaskSnapshot snapshot, PersonalAgentTaskRecord record) {
        if (!snapshot.status().terminal() || !observedTerminalTasks.add(snapshot.taskId())) {
            return;
        }
        if (snapshot.status() == TaskStatus.SUCCEEDED) {
            historyStore.append(PersonalAgentHistoryEntry.of(
                    record.userId(),
                    record.conversationId(),
                    record.channel(),
                    "assistant",
                    snapshot.content(),
                    Map.of("taskId", snapshot.taskId())
            ));
        } else {
            historyStore.append(PersonalAgentHistoryEntry.of(
                    record.userId(),
                    record.conversationId(),
                    record.channel(),
                    "system",
                    snapshot.errorMessage(),
                    Map.of("taskId", snapshot.taskId(), "status", snapshot.status().name())
            ));
        }
        auditStore.append(PersonalAgentAuditEvent.of(
                record.userId(),
                "personal_agent.task." + snapshot.status().name().toLowerCase(),
                snapshot.taskId(),
                Map.of("conversationId", record.conversationId(), "channel", record.channel())
        ));
    }

    private static long timeoutMillis(Map<String, Object> metadata) {
        Object value = metadata.get("timeoutMillis");
        if (value instanceof Number number) {
            return Math.max(0, number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Math.max(0, Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static TaskBackend defaultBackend(AgentRuntime runtime) {
        InMemoryTaskRegistry registry = new InMemoryTaskRegistry();
        registry.register(new PersonalAgentTaskHandler(runtime));
        InMemoryTaskEngine engine = new InMemoryTaskEngine(registry);
        return new TaskBackend(engine, engine);
    }

    private record TaskBackend(TaskEngine taskEngine, AutoCloseable closeable) {
    }
}
