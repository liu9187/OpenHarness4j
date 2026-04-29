package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.DefaultContextManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RetrievalAugmentedContextManager implements ContextManager {

    private static final String DEFAULT_CONTEXT_HEADER = "Relevant memory and knowledge:";

    private final ContextManager delegate;
    private final MemoryRetriever retriever;
    private final String namespace;
    private final int topK;
    private final double similarityThreshold;
    private final boolean indexCompletedMessages;

    public RetrievalAugmentedContextManager(MemoryRetriever retriever) {
        this(new DefaultContextManager(), retriever);
    }

    public RetrievalAugmentedContextManager(ContextManager delegate, MemoryRetriever retriever) {
        this(
                delegate,
                retriever,
                MemoryRetrievalRequest.DEFAULT_NAMESPACE,
                MemoryRetrievalRequest.DEFAULT_TOP_K,
                MemoryRetrievalRequest.DEFAULT_SIMILARITY_THRESHOLD,
                false
        );
    }

    public RetrievalAugmentedContextManager(
            ContextManager delegate,
            MemoryRetriever retriever,
            String namespace,
            int topK,
            double similarityThreshold,
            boolean indexCompletedMessages
    ) {
        this.delegate = delegate == null ? new DefaultContextManager() : delegate;
        this.retriever = Objects.requireNonNull(retriever, "retriever must not be null");
        this.namespace = namespace == null || namespace.isBlank() ? MemoryRetrievalRequest.DEFAULT_NAMESPACE : namespace;
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.indexCompletedMessages = indexCompletedMessages;
    }

    @Override
    public List<Message> init(AgentRequest request) {
        List<Message> messages = new ArrayList<>(delegate.init(request));
        List<MemoryRecord> records = retriever.retrieve(new MemoryRetrievalRequest(
                request.sessionId(),
                request.userId(),
                request.input(),
                namespace,
                topK,
                similarityThreshold,
                retrievalMetadata(request)
        ));
        if (records == null || records.isEmpty()) {
            return List.copyOf(messages);
        }
        messages.add(insertionIndex(messages), Message.system(formatRecords(records)));
        return List.copyOf(messages);
    }

    @Override
    public void complete(AgentRequest request, List<Message> messages) {
        delegate.complete(request, messages);
        if (!indexCompletedMessages) {
            return;
        }
        String content = completedConversationContent(messages);
        if (content.isBlank()) {
            return;
        }
        retriever.save(new MemoryRecord(null, content, saveMetadata(request), null));
    }

    private Map<String, Object> retrievalMetadata(AgentRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("sessionId", request.sessionId());
        metadata.put("userId", request.userId());
        metadata.put("namespace", namespace);
        metadata.put("source", "openharness");
        return metadata;
    }

    private Map<String, Object> saveMetadata(AgentRequest request) {
        Map<String, Object> metadata = retrievalMetadata(request);
        metadata.put("kind", "conversation");
        return metadata;
    }

    private static int insertionIndex(List<Message> messages) {
        int index = 0;
        while (index < messages.size() && messages.get(index).role() == MessageRole.SYSTEM) {
            index++;
        }
        return index;
    }

    private static String formatRecords(List<MemoryRecord> records) {
        StringBuilder builder = new StringBuilder(DEFAULT_CONTEXT_HEADER);
        int index = 1;
        for (MemoryRecord record : records) {
            if (record == null || record.content().isBlank()) {
                continue;
            }
            builder.append(System.lineSeparator())
                    .append('[')
                    .append(index++)
                    .append("] ")
                    .append(record.content());
        }
        return builder.toString();
    }

    private static String completedConversationContent(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        List<Message> conversational = messages.stream()
                .filter(message -> message.role() == MessageRole.USER || message.role() == MessageRole.ASSISTANT)
                .filter(message -> !message.content().isBlank())
                .toList();
        int from = Math.max(0, conversational.size() - 2);
        StringBuilder builder = new StringBuilder();
        for (Message message : conversational.subList(from, conversational.size())) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(message.role().name().toLowerCase()).append(": ").append(message.content());
        }
        return builder.toString();
    }
}
