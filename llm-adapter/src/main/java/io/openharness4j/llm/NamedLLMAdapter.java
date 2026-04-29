package io.openharness4j.llm;

public record NamedLLMAdapter(
        String name,
        LLMAdapter adapter
) {
    public NamedLLMAdapter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
    }
}
