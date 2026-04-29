package io.openharness4j.memory;

import java.util.List;

public interface MemoryRetriever {

    List<MemoryRecord> retrieve(MemoryRetrievalRequest request);

    default void save(MemoryRecord record) {
    }

    default void delete(List<String> ids) {
    }
}
