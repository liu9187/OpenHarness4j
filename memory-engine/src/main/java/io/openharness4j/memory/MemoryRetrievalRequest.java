package io.openharness4j.memory;

import java.util.Map;

public record MemoryRetrievalRequest(
        String sessionId,
        String userId,
        String query,
        String namespace,
        int topK,
        double similarityThreshold,
        Map<String, Object> metadata
) {
    public static final String DEFAULT_NAMESPACE = "openharness";
    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.0;

    public MemoryRetrievalRequest {
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        query = requireText(query, "query");
        namespace = namespace == null || namespace.isBlank() ? DEFAULT_NAMESPACE : namespace;
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static MemoryRetrievalRequest of(String sessionId, String userId, String query) {
        return new MemoryRetrievalRequest(
                sessionId,
                userId,
                query,
                DEFAULT_NAMESPACE,
                DEFAULT_TOP_K,
                DEFAULT_SIMILARITY_THRESHOLD,
                Map.of()
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
