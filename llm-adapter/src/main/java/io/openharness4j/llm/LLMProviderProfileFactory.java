package io.openharness4j.llm;

import java.util.List;
import java.util.Optional;

public class LLMProviderProfileFactory {

    private final ProviderValueResolver resolver;

    public LLMProviderProfileFactory() {
        this(ProviderValueResolver.systemEnvironment());
    }

    public LLMProviderProfileFactory(ProviderValueResolver resolver) {
        this.resolver = resolver == null ? ProviderValueResolver.systemEnvironment() : resolver;
    }

    public InMemoryLLMAdapterRegistry registry(List<LLMProviderProfile> profiles) {
        InMemoryLLMAdapterRegistry registry = new InMemoryLLMAdapterRegistry();
        if (profiles == null) {
            return registry;
        }
        for (LLMProviderProfile profile : profiles) {
            if (profile.enabled()) {
                registry.register(profile.name(), adapter(profile));
            }
        }
        return registry;
    }

    public Optional<LLMAdapter> adapter(LLMAdapterRegistry registry, String defaultProfile, List<String> fallbackOrder) {
        return new LLMProviderProfileSelector(defaultProfile, fallbackOrder).select(registry);
    }

    public Optional<LLMAdapter> adapter(List<LLMProviderProfile> profiles, String defaultProfile, List<String> fallbackOrder) {
        return adapter(registry(profiles), defaultProfile, fallbackOrder);
    }

    public LLMAdapter adapter(LLMProviderProfile profile) {
        String model = profile.resolvedModel(resolver);
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank for provider profile: " + profile.name());
        }
        return new OpenAICompatibleLLMAdapter(profile.endpoint(), profile.resolvedApiKey(resolver), model);
    }
}
