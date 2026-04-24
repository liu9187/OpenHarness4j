package io.openharness4j.runtime;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;

import java.util.List;

public class DefaultContextManager implements ContextManager {

    @Override
    public List<Message> init(AgentRequest request) {
        return List.of(Message.user(request.input()));
    }
}
