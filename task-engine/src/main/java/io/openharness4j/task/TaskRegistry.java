package io.openharness4j.task;

import java.util.List;
import java.util.Optional;

public interface TaskRegistry {

    void register(TaskHandler handler);

    Optional<TaskHandler> get(String type);

    List<TaskHandler> list();
}
