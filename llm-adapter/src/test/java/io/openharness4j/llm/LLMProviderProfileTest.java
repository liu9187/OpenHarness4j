package io.openharness4j.llm;

import io.openharness4j.api.LLMResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMProviderProfileTest {

    @Test
    void resolvesModelAndApiKeyFromEnvironmentResolver() {
        LLMProviderProfile profile = new LLMProviderProfile(
                "local",
                "http://localhost:11434/v1/chat/completions",
                "",
                "API_KEY",
                "",
                "MODEL",
                true
        );
        Map<String, String> values = Map.of("API_KEY", "secret", "MODEL", "llama");
        ProviderValueResolver resolver = values::get;

        assertEquals("secret", profile.resolvedApiKey(resolver));
        assertEquals("llama", profile.resolvedModel(resolver));
    }

    @Test
    void selectorBuildsFallbackAdapterInConfiguredOrder() {
        InMemoryLLMAdapterRegistry registry = new InMemoryLLMAdapterRegistry();
        AtomicInteger primaryCalls = new AtomicInteger();
        registry.register("primary", (messages, tools) -> {
            primaryCalls.incrementAndGet();
            throw new LLMAdapterException("primary down");
        });
        registry.register("fallback", (messages, tools) -> LLMResponse.text("fallback ok"));

        LLMAdapter adapter = new LLMProviderProfileSelector("primary", List.of("primary", "fallback"))
                .select(registry)
                .orElseThrow();

        assertEquals("fallback ok", adapter.chat(List.of(), List.of()).message().content());
        assertEquals(1, primaryCalls.get());
    }

    @Test
    void factoryRegistersEnabledProfiles() {
        LLMProviderProfileFactory factory = new LLMProviderProfileFactory(name -> "env-model");
        InMemoryLLMAdapterRegistry registry = factory.registry(List.of(
                new LLMProviderProfile("enabled", "http://localhost/v1/chat/completions", "", "", "model-a", "", true),
                new LLMProviderProfile("disabled", "http://localhost/v1/chat/completions", "", "", "model-b", "", false)
        ));

        assertTrue(registry.get("enabled").isPresent());
        assertTrue(registry.get("disabled").isEmpty());
    }
}
