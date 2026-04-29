package io.openharness4j.personal;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPersonalAgentTaskStore implements PersonalAgentTaskStore {

    private final ConcurrentMap<String, PersonalAgentTaskRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(PersonalAgentTaskRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        records.put(record.taskId(), record);
    }

    @Override
    public Optional<PersonalAgentTaskRecord> get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(taskId));
    }

    @Override
    public List<PersonalAgentTaskRecord> listByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return records.values().stream()
                .filter(record -> userId.equals(record.userId()))
                .toList();
    }
}
