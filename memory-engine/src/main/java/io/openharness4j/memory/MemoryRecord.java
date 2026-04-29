package io.openharness4j.memory;

import java.util.Map;
import java.util.UUID;

public record MemoryRecord(
        String id,
        String content,
        Map<String, Object> metadata,
        Double score
) {
    public MemoryRecord {
        id = id == null || id.isBlank() ? "memory_" + UUID.randomUUID() : id;
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static MemoryRecord of(String content) {
        return new MemoryRecord(null, content, Map.of(), null);
    }

    public static MemoryRecord of(String content, Map<String, Object> metadata) {
        return new MemoryRecord(null, content, metadata, null);
    }
}
