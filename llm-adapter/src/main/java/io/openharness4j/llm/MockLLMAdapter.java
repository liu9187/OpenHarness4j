package io.openharness4j.llm;

import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.ToolDefinition;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiFunction;

public class MockLLMAdapter implements LLMAdapter {

    private final Queue<LLMResponse> scriptedResponses = new ArrayDeque<>();
    private final BiFunction<List<Message>, List<ToolDefinition>, LLMResponse> fallback;

    public MockLLMAdapter(List<LLMResponse> scriptedResponses) {
        this(scriptedResponses, null);
    }

    public MockLLMAdapter(
            List<LLMResponse> scriptedResponses,
            BiFunction<List<Message>, List<ToolDefinition>, LLMResponse> fallback
    ) {
        if (scriptedResponses != null) {
            this.scriptedResponses.addAll(scriptedResponses);
        }
        this.fallback = fallback;
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools) {
        if (!scriptedResponses.isEmpty()) {
            return scriptedResponses.poll();
        }
        if (fallback != null) {
            return Objects.requireNonNull(fallback.apply(messages, tools), "fallback response must not be null");
        }
        return LLMResponse.text("No scripted mock response is available.");
    }
}
