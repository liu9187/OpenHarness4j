package io.openharness4j.personal.channel;

import io.openharness4j.personal.PersonalAgentMessage;

import java.util.Map;

public class DiscordChannelAdapter implements PersonalAgentChannelAdapter {

    @Override
    public String channel() {
        return "discord";
    }

    @Override
    public PersonalAgentMessage toMessage(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        return new PersonalAgentMessage(
                channel(),
                DirectChannelAdapter.text(safe, "channel_id", "conversationId"),
                DirectChannelAdapter.text(safe, "author_id", "user_id", "userId"),
                DirectChannelAdapter.text(safe, "content", "text"),
                DirectChannelAdapter.metadata(safe)
        );
    }
}
