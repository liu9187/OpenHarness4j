package io.openharness4j.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LLMProviderProfileSelector {

    private final String defaultProfile;
    private final List<String> fallbackOrder;

    public LLMProviderProfileSelector(String defaultProfile, List<String> fallbackOrder) {
        this.defaultProfile = defaultProfile == null ? "" : defaultProfile;
        this.fallbackOrder = fallbackOrder == null ? List.of() : List.copyOf(fallbackOrder);
    }

    public Optional<LLMAdapter> select(LLMAdapterRegistry registry) {
        if (registry == null) {
            return Optional.empty();
        }
        List<String> names = orderedNames(registry);
        List<LLMAdapter> adapters = new ArrayList<>();
        for (String name : names) {
            registry.get(name).ifPresent(adapters::add);
        }
        if (adapters.isEmpty()) {
            return Optional.empty();
        }
        if (adapters.size() == 1) {
            return Optional.of(adapters.get(0));
        }
        return Optional.of(new FallbackLLMAdapter(adapters));
    }

    private List<String> orderedNames(LLMAdapterRegistry registry) {
        List<String> names = new ArrayList<>();
        if (!fallbackOrder.isEmpty()) {
            for (String name : fallbackOrder) {
                if (name != null && !name.isBlank() && !names.contains(name)) {
                    names.add(name);
                }
            }
            return names;
        }
        if (!defaultProfile.isBlank()) {
            names.add(defaultProfile);
        }
        for (String name : registry.names()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }
}
