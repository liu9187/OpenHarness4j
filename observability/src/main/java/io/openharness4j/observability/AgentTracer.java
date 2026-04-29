package io.openharness4j.observability;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.FinishReason;

public interface AgentTracer {

    AgentTrace start(AgentRequest request);

    default void finish(AgentTrace trace, FinishReason finishReason) {
    }
}
