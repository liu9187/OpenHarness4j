package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.util.ArrayList;
import java.util.List;

public record MemoryWindowPolicy(
        int maxMessages,
        boolean summarizeOverflow,
        MemorySummarizer summarizer
) {
    public static final int DEFAULT_MAX_MESSAGES = 20;

    public MemoryWindowPolicy {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be greater than zero");
        }
        summarizer = summarizer == null ? new SimpleMemorySummarizer() : summarizer;
    }

    public static MemoryWindowPolicy defaults() {
        return new MemoryWindowPolicy(DEFAULT_MAX_MESSAGES, true, new SimpleMemorySummarizer());
    }

    public static MemoryWindowPolicy tailOnly(int maxMessages) {
        return new MemoryWindowPolicy(maxMessages, false, new SimpleMemorySummarizer());
    }

    public List<Message> apply(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() <= maxMessages) {
            return List.copyOf(messages);
        }

        if (!summarizeOverflow || maxMessages == 1) {
            return tail(messages, maxMessages);
        }

        int tailSize = maxMessages - 1;
        List<Message> dropped = messages.subList(0, messages.size() - tailSize);
        List<Message> kept = tail(messages, tailSize);
        List<Message> compacted = new ArrayList<>();
        compacted.add(summarizer.summarize(dropped));
        compacted.addAll(kept);
        return List.copyOf(compacted);
    }

    private static List<Message> tail(List<Message> messages, int count) {
        return List.copyOf(messages.subList(messages.size() - count, messages.size()));
    }
}
