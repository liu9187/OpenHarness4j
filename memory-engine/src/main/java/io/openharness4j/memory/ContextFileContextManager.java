package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.Message;
import io.openharness4j.runtime.ContextManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ContextFileContextManager implements ContextManager {

    public static final String CLAUDE_PREFIX = "Project instructions from CLAUDE.md:\n";
    public static final String MEMORY_PREFIX = "Persistent memory from MEMORY.md:\n";

    private final ContextManager delegate;
    private final Path baseDirectory;
    private final boolean loadClaude;
    private final boolean loadMemory;
    private final boolean persistMemory;
    private final MemorySummarizer summarizer;

    public ContextFileContextManager(ContextManager delegate, Path baseDirectory) {
        this(delegate, baseDirectory, true, true, false, new SimpleMemorySummarizer());
    }

    public ContextFileContextManager(
            ContextManager delegate,
            Path baseDirectory,
            boolean loadClaude,
            boolean loadMemory,
            boolean persistMemory,
            MemorySummarizer summarizer
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.baseDirectory = baseDirectory == null
                ? Path.of(".").toAbsolutePath().normalize()
                : baseDirectory.toAbsolutePath().normalize();
        this.loadClaude = loadClaude;
        this.loadMemory = loadMemory;
        this.persistMemory = persistMemory;
        this.summarizer = summarizer == null ? new SimpleMemorySummarizer() : summarizer;
    }

    @Override
    public List<Message> init(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        if (loadClaude) {
            discover("CLAUDE.md").ifPresent(content -> messages.add(Message.system(CLAUDE_PREFIX + content)));
        }
        if (loadMemory) {
            discover("MEMORY.md").ifPresent(content -> messages.add(Message.system(MEMORY_PREFIX + content)));
        }
        messages.addAll(delegate.init(request));
        return List.copyOf(messages);
    }

    @Override
    public void complete(AgentRequest request, List<Message> messages) {
        List<Message> cleaned = stripContextFileMessages(messages);
        delegate.complete(request, cleaned);
        if (persistMemory) {
            persistMemory(cleaned);
        }
    }

    private Optional<String> discover(String fileName) {
        Path current = baseDirectory;
        while (current != null) {
            Path candidate = current.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                try {
                    String content = Files.readString(candidate).trim();
                    if (!content.isBlank()) {
                        return Optional.of(content);
                    }
                } catch (IOException ex) {
                    throw new MemoryStoreException("failed to read context file: " + candidate, ex);
                }
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private void persistMemory(List<Message> messages) {
        Message summary = summarizer.summarize(stripContextFileMessages(messages));
        Path memoryFile = baseDirectory.resolve("MEMORY.md");
        try {
            Files.createDirectories(baseDirectory);
            Files.writeString(memoryFile, summary.content());
        } catch (IOException ex) {
            throw new MemoryStoreException("failed to write MEMORY.md: " + memoryFile, ex);
        }
    }

    private static List<Message> stripContextFileMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> !message.content().startsWith(CLAUDE_PREFIX))
                .filter(message -> !message.content().startsWith(MEMORY_PREFIX))
                .toList();
    }
}
