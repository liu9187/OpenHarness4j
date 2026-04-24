package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.util.List;
import java.util.stream.Collectors;

public class SimpleMemorySummarizer implements MemorySummarizer {

    private final int maxCharacters;

    public SimpleMemorySummarizer() {
        this(800);
    }

    public SimpleMemorySummarizer(int maxCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("maxCharacters must be greater than zero");
        }
        this.maxCharacters = maxCharacters;
    }

    @Override
    public Message summarize(List<Message> messages) {
        String summary = messages == null || messages.isEmpty()
                ? "Previous conversation summary: empty."
                : messages.stream()
                .map(message -> message.role().name().toLowerCase() + ": " + message.content())
                .collect(Collectors.joining(" | "));
        if (summary.length() > maxCharacters) {
            summary = summary.substring(0, maxCharacters) + "...";
        }
        return Message.system("Previous conversation summary: " + summary);
    }
}
