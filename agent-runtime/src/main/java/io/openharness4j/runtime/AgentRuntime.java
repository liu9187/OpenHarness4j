package io.openharness4j.runtime;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;

public interface AgentRuntime {

    AgentResponse run(AgentRequest request);
}
