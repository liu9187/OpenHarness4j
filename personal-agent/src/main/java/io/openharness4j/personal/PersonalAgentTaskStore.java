package io.openharness4j.personal;

import java.util.List;
import java.util.Optional;

public interface PersonalAgentTaskStore {

    void save(PersonalAgentTaskRecord record);

    Optional<PersonalAgentTaskRecord> get(String taskId);

    List<PersonalAgentTaskRecord> listByUser(String userId);
}
