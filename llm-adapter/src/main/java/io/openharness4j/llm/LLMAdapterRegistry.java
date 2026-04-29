package io.openharness4j.llm;

import java.util.List;
import java.util.Optional;

public interface LLMAdapterRegistry {

    void register(String name, LLMAdapter adapter);

    Optional<LLMAdapter> get(String name);

    List<String> names();
}
