package io.openharness4j.llm;

import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.ToolDefinition;

import java.util.List;

public class FallbackLLMAdapter implements LLMAdapter {

    private final List<LLMAdapter> adapters;

    public FallbackLLMAdapter(List<LLMAdapter> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalArgumentException("adapters must not be empty");
        }
        this.adapters = List.copyOf(adapters);
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools) {
        RuntimeException lastError = null;
        for (LLMAdapter adapter : adapters) {
            try {
                return adapter.chat(messages, tools);
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }
        throw new LLMAdapterException("all LLM adapters failed", lastError);
    }
}
