package io.openharness4j.personal.channel;

import io.openharness4j.personal.PersonalAgentMessage;

import java.util.Map;

public class FeishuChannelAdapter implements PersonalAgentChannelAdapter {

    @Override
    public String channel() {
        return "feishu";
    }

    @Override
    public PersonalAgentMessage toMessage(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        return new PersonalAgentMessage(
                channel(),
                DirectChannelAdapter.text(safe, "chat_id", "open_chat_id", "conversationId"),
                DirectChannelAdapter.text(safe, "sender_id", "open_id", "userId"),
                DirectChannelAdapter.text(safe, "text", "content"),
                DirectChannelAdapter.metadata(safe)
        );
    }
}
