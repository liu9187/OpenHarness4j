package io.openharness4j.personal.channel;

import io.openharness4j.personal.PersonalAgentMessage;

import java.util.LinkedHashMap;
import java.util.Map;

public class DirectChannelAdapter implements PersonalAgentChannelAdapter {

    @Override
    public String channel() {
        return "direct";
    }

    @Override
    public PersonalAgentMessage toMessage(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        return new PersonalAgentMessage(
                channel(),
                text(safe, "conversationId", "sessionId"),
                text(safe, "userId", "user"),
                text(safe, "text", "message"),
                metadata(safe)
        );
    }

    static String text(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
            if (value != null) {
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        throw new IllegalArgumentException("payload missing text field: " + String.join("/", keys));
    }

    static Map<String, Object> metadata(Map<String, Object> payload) {
        Map<String, Object> metadata = new LinkedHashMap<>(payload);
        metadata.remove("conversationId");
        metadata.remove("sessionId");
        metadata.remove("userId");
        metadata.remove("user");
        metadata.remove("text");
        metadata.remove("message");
        return metadata;
    }
}
