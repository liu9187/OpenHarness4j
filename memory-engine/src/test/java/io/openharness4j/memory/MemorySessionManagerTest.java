package io.openharness4j.memory;

import io.openharness4j.api.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySessionManagerTest {

    @Test
    void resumesAndClearsSessionHistory() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemorySessionManager manager = new MemorySessionManager(store);

        manager.append("session-1", Message.user("hello"));
        manager.replace("session-1", List.of(Message.user("hello"), Message.assistant("hi")));

        assertEquals(2, manager.resume("session-1").size());
        manager.clear("session-1");
        assertTrue(manager.resume("session-1").isEmpty());
    }
}
