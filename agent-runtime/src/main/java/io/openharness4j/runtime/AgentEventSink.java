package io.openharness4j.runtime;

@FunctionalInterface
public interface AgentEventSink {

    AgentEventSink NOOP = event -> {
    };

    void accept(AgentEvent event);

    static AgentEventSink noop() {
        return NOOP;
    }
}
