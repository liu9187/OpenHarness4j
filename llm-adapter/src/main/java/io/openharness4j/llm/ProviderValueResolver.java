package io.openharness4j.llm;

@FunctionalInterface
public interface ProviderValueResolver {

    String resolve(String name);

    static ProviderValueResolver systemEnvironment() {
        return System::getenv;
    }
}
