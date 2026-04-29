package io.openharness4j.task;

public interface TaskHandler {

    String type();

    TaskResult handle(TaskContext context) throws Exception;
}
