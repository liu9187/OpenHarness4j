package io.openharness4j.springai;

import io.openharness4j.memory.MemoryRecord;
import io.openharness4j.memory.MemoryRetrievalRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiVectorStoreTest {

    @Test
    void mapsRetrievalRequestToSpringAiSearchRequest() {
        RecordingVectorStore vectorStore = new RecordingVectorStore(List.of(
                Document.builder()
                        .id("doc-1")
                        .text("policy text")
                        .metadata(Map.of("namespace", "support"))
                        .score(0.92)
                        .build()
        ));
        SpringAiVectorStore adapter = new SpringAiVectorStore(vectorStore, "support");

        List<MemoryRecord> records = adapter.retrieve(new MemoryRetrievalRequest(
                "s1",
                "u1",
                "refund",
                "support",
                4,
                0.65,
                Map.of("tenant", "acme")
        ));

        assertEquals("refund", vectorStore.lastRequest.getQuery());
        assertEquals(4, vectorStore.lastRequest.getTopK());
        assertEquals(0.65, vectorStore.lastRequest.getSimilarityThreshold());
        assertTrue(vectorStore.lastRequest.hasFilterExpression());
        assertEquals("policy text", records.get(0).content());
        assertEquals(0.92, records.get(0).score());
    }

    @Test
    void savesMemoryRecordAsSpringAiDocumentWithMetadata() {
        RecordingVectorStore vectorStore = new RecordingVectorStore(List.of());
        SpringAiVectorStore adapter = new SpringAiVectorStore(vectorStore, "support");

        adapter.save(new MemoryRecord("m1", "remember this", Map.of("sessionId", "s1", "namespace", "support"), null));

        assertEquals(1, vectorStore.added.size());
        Document document = vectorStore.added.get(0);
        assertEquals("m1", document.getId());
        assertEquals("remember this", document.getText());
        assertEquals("s1", document.getMetadata().get("sessionId"));
        assertEquals("support", document.getMetadata().get("namespace"));
    }

    private static final class RecordingVectorStore implements VectorStore {
        private final List<Document> searchResults;
        private final List<Document> added = new ArrayList<>();
        private SearchRequest lastRequest;

        private RecordingVectorStore(List<Document> searchResults) {
            this.searchResults = searchResults;
        }

        @Override
        public void add(List<Document> documents) {
            added.addAll(documents);
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            lastRequest = request;
            return searchResults;
        }
    }
}
