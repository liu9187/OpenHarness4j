package io.openharness4j.runtime;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;

import java.util.List;

public interface ContextManager {

    List<Message> init(AgentRequest request);
}
