package io.openharness4j.personal.team;

import io.openharness4j.personal.InMemoryPersonalAgentAuditStore;
import io.openharness4j.personal.PersonalAgentAuditEvent;
import io.openharness4j.personal.PersonalAgentAuditStore;
import io.openharness4j.task.InMemoryTaskEngine;
import io.openharness4j.task.InMemoryTaskRegistry;
import io.openharness4j.task.TaskRequest;
import io.openharness4j.task.TaskSnapshot;
import io.openharness4j.task.TaskSubmission;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryTeamRuntime implements TeamRuntime, AutoCloseable {

    private final TeamAgentRegistry registry;
    private final InMemoryTaskEngine taskEngine;
    private final PersonalAgentAuditStore auditStore;
    private final ConcurrentMap<String, TeamAgentArchive> archives = new ConcurrentHashMap<>();

    public InMemoryTeamRuntime(TeamAgentRegistry registry) {
        this(registry, new InMemoryPersonalAgentAuditStore(), 0, 4);
    }

    public InMemoryTeamRuntime(
            TeamAgentRegistry registry,
            PersonalAgentAuditStore auditStore,
            long defaultTimeoutMillis,
            int poolSize
    ) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.auditStore = Objects.requireNonNull(auditStore, "auditStore must not be null");
        InMemoryTaskRegistry taskRegistry = new InMemoryTaskRegistry();
        taskRegistry.register(new TeamAgentTaskHandler(registry));
        this.taskEngine = new InMemoryTaskEngine(taskRegistry, defaultTimeoutMillis, poolSize);
    }

    @Override
    public TeamAgentSubmission spawn(TeamAgentRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        TeamAgentDefinition agent = registry.get(request.agentId())
                .orElseThrow(() -> new IllegalArgumentException("team agent not found: " + request.agentId()));
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(request.metadata());
        metadata.put("agentId", agent.agentId());
        metadata.put("role", agent.role());
        metadata.put("userId", request.userId());
        metadata.put("sessionId", request.sessionId());
        TaskSubmission submission = taskEngine.submit(new TaskRequest(
                TeamAgentTaskHandler.DEFAULT_TYPE,
                Map.of(
                        TeamAgentTaskHandler.INPUT_AGENT_ID, request.agentId(),
                        TeamAgentTaskHandler.INPUT_SESSION_ID, request.sessionId(),
                        TeamAgentTaskHandler.INPUT_USER_ID, request.userId(),
                        TeamAgentTaskHandler.INPUT_INSTRUCTION, request.instruction()
                ),
                metadata,
                request.timeoutMillis()
        ));
        auditStore.append(PersonalAgentAuditEvent.of(
                request.userId(),
                "team_runtime.agent.spawned",
                submission.taskId(),
                Map.of("agentId", request.agentId(), "sessionId", request.sessionId())
        ));
        return new TeamAgentSubmission(submission.taskId(), request.agentId(), submission.status());
    }

    @Override
    public Optional<TeamAgentSnapshot> get(String taskId) {
        return taskEngine.get(taskId).map(TeamAgentSnapshot::from);
    }

    @Override
    public boolean cancel(String taskId) {
        boolean cancelled = taskEngine.cancel(taskId);
        if (cancelled) {
            get(taskId).ifPresent(snapshot -> auditStore.append(PersonalAgentAuditEvent.of(
                    String.valueOf(snapshot.metadata().getOrDefault("userId", "")),
                    "team_runtime.agent.cancelled",
                    taskId,
                    Map.of("agentId", snapshot.agentId())
            )));
        }
        return cancelled;
    }

    @Override
    public Optional<TeamAgentArchive> archive(String taskId) {
        Optional<TaskSnapshot> snapshot = taskEngine.get(taskId);
        if (snapshot.isEmpty() || !snapshot.get().status().terminal()) {
            return Optional.empty();
        }
        TeamAgentSnapshot teamSnapshot = TeamAgentSnapshot.from(snapshot.get());
        TeamAgentArchive archive = new TeamAgentArchive(
                "archive_" + UUID.randomUUID(),
                teamSnapshot,
                Instant.now()
        );
        TeamAgentArchive existing = archives.putIfAbsent(taskId, archive);
        TeamAgentArchive stored = existing == null ? archive : existing;
        auditStore.append(PersonalAgentAuditEvent.of(
                String.valueOf(teamSnapshot.metadata().getOrDefault("userId", "")),
                "team_runtime.agent.archived",
                taskId,
                Map.of("agentId", teamSnapshot.agentId(), "archiveId", stored.archiveId())
        ));
        return Optional.of(stored);
    }

    public Optional<TeamAgentArchive> archived(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(archives.get(taskId));
    }

    public PersonalAgentAuditStore auditStore() {
        return auditStore;
    }

    @Override
    public void close() {
        taskEngine.close();
    }
}
