package io.openharness4j.personal;

import java.util.Map;

public record PersonalAgentMessage(
        String channel,
        String conversationId,
        String userId,
        String text,
        Map<String, Object> metadata
) {
    public PersonalAgentMessage {
        channel = channel == null || channel.isBlank() ? "direct" : channel;
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static PersonalAgentMessage of(String channel, String conversationId, String userId, String text) {
        return new PersonalAgentMessage(channel, conversationId, userId, text, Map.of());
    }
}
