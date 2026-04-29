package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;
import io.openharness4j.runtime.DefaultContextManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalAugmentedContextManagerTest {

    @Test
    void injectsRetrievedRecordsAsSystemContext() {
        RecordingRetriever retriever = new RecordingRetriever(List.of(
                new MemoryRecord("doc-1", "Use the refund policy.", Map.of("source", "kb"), 0.91)
        ));
        RetrievalAugmentedContextManager manager = new RetrievalAugmentedContextManager(
                new DefaultContextManager(),
                retriever,
                "support",
                3,
                0.7,
                false
        );

        List<Message> messages = manager.init(AgentRequest.of("s1", "u1", "refund?"));

        assertEquals("support", retriever.lastRequest.namespace());
        assertEquals(3, retriever.lastRequest.topK());
        assertEquals(0.7, retriever.lastRequest.similarityThreshold());
        assertEquals("Relevant memory and knowledge:\n[1] Use the refund policy.", messages.get(0).content());
        assertEquals("refund?", messages.get(1).content());
    }

    @Test
    void indexesCompletedConversationWhenEnabled() {
        RecordingRetriever retriever = new RecordingRetriever(List.of());
        RetrievalAugmentedContextManager manager = new RetrievalAugmentedContextManager(
                new DefaultContextManager(),
                retriever,
                "support",
                3,
                0.0,
                true
        );

        manager.complete(AgentRequest.of("s1", "u1", "hello"), List.of(
                Message.user("hello"),
                Message.assistant("hi")
        ));

        assertEquals(1, retriever.saved.size());
        assertEquals("user: hello\nassistant: hi", retriever.saved.get(0).content());
        assertEquals("s1", retriever.saved.get(0).metadata().get("sessionId"));
        assertEquals("support", retriever.saved.get(0).metadata().get("namespace"));
    }

    private static final class RecordingRetriever implements MemoryRetriever {
        private final List<MemoryRecord> records;
        private final List<MemoryRecord> saved = new ArrayList<>();
        private MemoryRetrievalRequest lastRequest;

        private RecordingRetriever(List<MemoryRecord> records) {
            this.records = records;
        }

        @Override
        public List<MemoryRecord> retrieve(MemoryRetrievalRequest request) {
            lastRequest = request;
            return records;
        }

        @Override
        public void save(MemoryRecord record) {
            saved.add(record);
        }
    }
}
