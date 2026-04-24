package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryContextManagerTest {

    @Test
    void loadsSessionMemoryBeforeCurrentUserInput() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        store.replace("s1", List.of(Message.user("remember this"), Message.assistant("remembered")));
        MemoryContextManager manager = new MemoryContextManager(store, MemoryWindowPolicy.tailOnly(10));

        List<Message> messages = manager.init(AgentRequest.of("s1", "u1", "what do you remember?"));

        assertEquals(3, messages.size());
        assertEquals("remember this", messages.get(0).content());
        assertEquals("remembered", messages.get(1).content());
        assertEquals("what do you remember?", messages.get(2).content());
    }

    @Test
    void savesCompletedConversationBackToMemory() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryContextManager manager = new MemoryContextManager(store, MemoryWindowPolicy.tailOnly(10));
        AgentRequest request = AgentRequest.of("s1", "u1", "hello");
        List<Message> messages = List.of(Message.user("hello"), Message.assistant("hi"));

        manager.complete(request, messages);

        assertEquals(messages, store.load("s1"));
    }

    @Test
    void appliesWindowPolicyWhenSavingConversation() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryContextManager manager = new MemoryContextManager(store, MemoryWindowPolicy.tailOnly(2));
        AgentRequest request = AgentRequest.of("s1", "u1", "third");

        manager.complete(request, List.of(
                Message.user("first"),
                Message.assistant("second"),
                Message.user("third")
        ));

        assertEquals(List.of(Message.assistant("second"), Message.user("third")), store.load("s1"));
    }
}
