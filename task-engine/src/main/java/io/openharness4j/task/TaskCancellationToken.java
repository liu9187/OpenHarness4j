package io.openharness4j.task;

@FunctionalInterface
public interface TaskCancellationToken {

    boolean cancellationRequested();
}
