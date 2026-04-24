package io.openharness4j.memory;

import io.openharness4j.api.Message;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoredMessageCodecTest {

    @Test
    void roundTripsToolMessage() {
        Message message = Message.tool("call-1", "echo", "hello memory");

        Message decoded = StoredMessageCodec.decode(StoredMessageCodec.encode(message));

        assertEquals(message, decoded);
    }
}
