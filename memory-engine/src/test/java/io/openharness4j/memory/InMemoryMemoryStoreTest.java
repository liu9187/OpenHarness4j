package io.openharness4j.memory;

import io.openharness4j.api.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMemoryStoreTest {

    @Test
    void savesLoadsAndClearsMessagesBySession() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();

        store.save("s1", Message.user("hello"));
        store.save("s1", Message.assistant("hi"));
        store.save("s2", Message.user("other"));

        assertEquals(List.of(Message.user("hello"), Message.assistant("hi")), store.load("s1"));
        assertEquals(List.of(Message.user("other")), store.load("s2"));

        store.clear("s1");

        assertTrue(store.load("s1").isEmpty());
        assertEquals(1, store.load("s2").size());
    }

    @Test
    void replacesSessionMessages() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.save("s1", Message.user("old"));

        store.replace("s1", List.of(Message.user("new"), Message.assistant("done")));

        assertEquals(List.of(Message.user("new"), Message.assistant("done")), store.load("s1"));
    }

    @Test
    void rejectsBlankSessionId() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();

        assertThrows(IllegalArgumentException.class, () -> store.load(""));
    }
}
