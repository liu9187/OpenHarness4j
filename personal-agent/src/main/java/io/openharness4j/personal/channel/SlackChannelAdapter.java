package io.openharness4j.personal.channel;

import io.openharness4j.personal.PersonalAgentMessage;

import java.util.Map;

public class SlackChannelAdapter implements PersonalAgentChannelAdapter {

    @Override
    public String channel() {
        return "slack";
    }

    @Override
    public PersonalAgentMessage toMessage(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        return new PersonalAgentMessage(
                channel(),
                DirectChannelAdapter.text(safe, "channel_id", "channel", "conversationId"),
                DirectChannelAdapter.text(safe, "user_id", "user", "userId"),
                DirectChannelAdapter.text(safe, "text"),
                DirectChannelAdapter.metadata(safe)
        );
    }
}
