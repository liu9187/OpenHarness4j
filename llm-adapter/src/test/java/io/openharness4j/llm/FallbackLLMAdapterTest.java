package io.openharness4j.llm;

import io.openharness4j.api.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FallbackLLMAdapterTest {

    @Test
    void fallsBackToNextAdapterWhenFirstFails() {
        AtomicInteger calls = new AtomicInteger();
        FallbackLLMAdapter adapter = new FallbackLLMAdapter(List.of(
                (messages, tools) -> {
                    calls.incrementAndGet();
                    throw new LLMAdapterException("primary failed");
                },
                (messages, tools) -> {
                    calls.incrementAndGet();
                    return LLMResponse.text("fallback ok");
                }
        ));

        assertEquals("fallback ok", adapter.chat(List.of(), List.of()).message().content());
        assertEquals(2, calls.get());
    }

    @Test
    void failsWhenAllAdaptersFail() {
        FallbackLLMAdapter adapter = new FallbackLLMAdapter(List.of(
                (messages, tools) -> {
                    throw new LLMAdapterException("primary failed");
                }
        ));

        assertThrows(LLMAdapterException.class, () -> adapter.chat(List.of(), List.of()));
    }
}
