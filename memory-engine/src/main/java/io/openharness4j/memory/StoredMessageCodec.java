package io.openharness4j.memory;

import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

final class StoredMessageCodec {

    private StoredMessageCodec() {
    }

    static String encode(Message message) {
        return String.join(
                ".",
                message.role().name(),
                encode(message.content()),
                encode(message.name()),
                encode(message.toolCallId())
        );
    }

    static Message decode(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("invalid stored message");
        }
        return new Message(
                MessageRole.valueOf(parts[0]),
                decodePart(parts[1]),
                decodePart(parts[2]),
                decodePart(parts[3]),
                List.of()
        );
    }

    private static String encode(String value) {
        String normalized = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
