package io.openharness4j.personal.channel;

import io.openharness4j.personal.PersonalAgentMessage;

import java.util.Map;

public interface PersonalAgentChannelAdapter {

    String channel();

    PersonalAgentMessage toMessage(Map<String, Object> payload);
}
