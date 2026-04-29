package io.openharness4j.springai;

import io.openharness4j.memory.MemoryRecord;
import io.openharness4j.memory.MemoryRetrievalRequest;
import io.openharness4j.memory.MemoryRetriever;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SpringAiVectorStore implements MemoryRetriever {

    public static final String METADATA_NAMESPACE = "namespace";

    private final VectorStore vectorStore;
    private final String defaultNamespace;

    public SpringAiVectorStore(VectorStore vectorStore) {
        this(vectorStore, MemoryRetrievalRequest.DEFAULT_NAMESPACE);
    }

    public SpringAiVectorStore(VectorStore vectorStore, String defaultNamespace) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.defaultNamespace = defaultNamespace == null || defaultNamespace.isBlank()
                ? MemoryRetrievalRequest.DEFAULT_NAMESPACE
                : defaultNamespace;
    }

    @Override
    public List<MemoryRecord> retrieve(MemoryRetrievalRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<Document> documents = vectorStore.similaritySearch(toSearchRequest(request));
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<MemoryRecord> records = new ArrayList<>();
        for (Document document : documents) {
            String content = document.getText();
            if (content == null || content.isBlank()) {
                content = document.getFormattedContent();
            }
            if (content == null || content.isBlank()) {
                continue;
            }
            records.add(new MemoryRecord(document.getId(), content, document.getMetadata(), document.getScore()));
        }
        return List.copyOf(records);
    }

    @Override
    public void save(MemoryRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        Map<String, Object> metadata = simpleMetadata(record.metadata());
        metadata.putIfAbsent(METADATA_NAMESPACE, defaultNamespace);
        vectorStore.add(List.of(Document.builder()
                .id(record.id())
                .text(record.content())
                .metadata(metadata)
                .score(record.score())
                .build()));
    }

    @Override
    public void delete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        vectorStore.delete(ids);
    }

    SearchRequest toSearchRequest(MemoryRetrievalRequest request) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(request.query())
                .topK(request.topK());
        if (request.similarityThreshold() <= MemoryRetrievalRequest.DEFAULT_SIMILARITY_THRESHOLD) {
            builder.similarityThresholdAll();
        } else {
            builder.similarityThreshold(request.similarityThreshold());
        }
        String namespace = request.namespace() == null || request.namespace().isBlank()
                ? defaultNamespace
                : request.namespace();
        if (namespace != null && !namespace.isBlank()) {
            builder.filterExpression(METADATA_NAMESPACE + " == '" + escapeFilterValue(namespace) + "'");
        }
        return builder.build();
    }

    private static String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static Map<String, Object> simpleMetadata(Map<String, Object> metadata) {
        Map<String, Object> simple = new LinkedHashMap<>();
        if (metadata == null) {
            return simple;
        }
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                simple.put(key, value);
            }
        });
        return simple;
    }
}
