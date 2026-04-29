package io.openharness4j.personal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPersonalWorkspaceStore implements PersonalWorkspaceStore {

    private final ConcurrentMap<String, PersonalWorkspace> workspaces = new ConcurrentHashMap<>();

    @Override
    public PersonalWorkspace getOrCreate(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        return workspaces.compute(userId, (key, existing) -> {
            if (existing != null) {
                return existing.touch();
            }
            Instant now = Instant.now();
            return new PersonalWorkspace(
                    "workspace_" + UUID.randomUUID(),
                    key,
                    Map.of(),
                    now,
                    now
            );
        });
    }

    @Override
    public Optional<PersonalWorkspace> get(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(workspaces.get(userId));
    }

    @Override
    public void save(PersonalWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace must not be null");
        }
        workspaces.put(workspace.userId(), workspace);
    }

    @Override
    public List<PersonalWorkspace> list() {
        return new ArrayList<>(workspaces.values());
    }
}
