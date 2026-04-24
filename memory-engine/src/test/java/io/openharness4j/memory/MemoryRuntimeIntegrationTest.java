package io.openharness4j.memory;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.FinishReason;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.tool.InMemoryToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRuntimeIntegrationTest {

    @Test
    void remembersMessagesAcrossRuntimeCallsWithSameSession() {
        InMemoryMemoryStore store = new InMemoryMemoryStore();
        MemoryContextManager contextManager = new MemoryContextManager(store, MemoryWindowPolicy.tailOnly(10));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<Message>> secondRunMessages = new AtomicReference<>();
        LLMAdapter llmAdapter = (messages, tools) -> {
            if (calls.incrementAndGet() == 1) {
                return LLMResponse.text("remembered");
            }
            secondRunMessages.set(messages);
            return LLMResponse.text("I remember your first message.");
        };
        AgentRuntime runtime = new DefaultAgentRuntime(
                llmAdapter,
                new InMemoryToolRegistry(),
                new AllowAllPermissionChecker(),
                new DefaultAgentTracer(),
                contextManager,
                AgentRuntimeConfig.defaults()
        );

        AgentResponse first = runtime.run(AgentRequest.of("session-1", "user-1", "remember pineapple"));
        AgentResponse second = runtime.run(AgentRequest.of("session-1", "user-1", "what did I ask?"));

        assertEquals(FinishReason.STOP, first.finishReason());
        assertEquals(FinishReason.STOP, second.finishReason());
        assertTrue(secondRunMessages.get().stream().anyMatch(message -> message.content().equals("remember pineapple")));
        assertTrue(secondRunMessages.get().stream().anyMatch(message -> message.content().equals("remembered")));
        assertEquals(4, store.load("session-1").size());
    }
}
