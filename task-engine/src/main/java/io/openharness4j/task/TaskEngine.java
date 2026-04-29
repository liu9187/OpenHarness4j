package io.openharness4j.task;

import java.util.Optional;

public interface TaskEngine {

    TaskSubmission submit(TaskRequest request);

    Optional<TaskSnapshot> get(String taskId);

    boolean cancel(String taskId);
}
