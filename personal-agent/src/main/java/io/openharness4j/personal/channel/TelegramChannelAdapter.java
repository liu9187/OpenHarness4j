package io.openharness4j.personal.channel;

import io.openharness4j.personal.PersonalAgentMessage;

import java.util.Map;

public class TelegramChannelAdapter implements PersonalAgentChannelAdapter {

    @Override
    public String channel() {
        return "telegram";
    }

    @Override
    public PersonalAgentMessage toMessage(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        return new PersonalAgentMessage(
                channel(),
                DirectChannelAdapter.text(safe, "chat_id", "conversationId"),
                DirectChannelAdapter.text(safe, "from_id", "user_id", "userId"),
                DirectChannelAdapter.text(safe, "text"),
                DirectChannelAdapter.metadata(safe)
        );
    }
}
