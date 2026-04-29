package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextFileContextManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void injectsClaudeAndMemoryFilesAndDoesNotStoreInjectedMessages() throws Exception {
        Files.writeString(tempDir.resolve("CLAUDE.md"), "Use Java style.");
        Files.writeString(tempDir.resolve("MEMORY.md"), "User likes concise answers.");
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        ContextFileContextManager manager = new ContextFileContextManager(
                new MemoryContextManager(store, MemoryWindowPolicy.tailOnly(10)),
                tempDir,
                true,
                true,
                false,
                new SimpleMemorySummarizer()
        );

        List<Message> init = manager.init(AgentRequest.of("session-1", "user-1", "hello"));

        assertTrue(init.get(0).content().startsWith(ContextFileContextManager.CLAUDE_PREFIX));
        assertTrue(init.get(1).content().startsWith(ContextFileContextManager.MEMORY_PREFIX));
        manager.complete(AgentRequest.of("session-1", "user-1", "hello"), List.of(
                init.get(0),
                init.get(1),
                Message.user("hello"),
                Message.assistant("hi")
        ));
        assertEquals(2, store.load("session-1").size());
    }

    @Test
    void persistsMemoryFileWhenEnabled() {
        ContextFileContextManager manager = new ContextFileContextManager(
                new MemoryContextManager(new InMemoryMemoryStore(), MemoryWindowPolicy.tailOnly(10)),
                tempDir,
                false,
                false,
                true,
                new SimpleMemorySummarizer(200)
        );

        manager.complete(AgentRequest.of("session-1", "user-1", "remember"), List.of(
                Message.user("remember mango"),
                Message.assistant("stored mango")
        ));

        assertTrue(Files.exists(tempDir.resolve("MEMORY.md")));
        assertTrue(read(tempDir.resolve("MEMORY.md")).contains("mango"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
