package io.openharness4j.observability;

import io.openharness4j.api.AgentRequest;

public interface AgentTracer {

    AgentTrace start(AgentRequest request);
}
