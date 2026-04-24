package io.openharness4j.runtime;

public record AgentRuntimeConfig(int maxIterations) {

    public static final int DEFAULT_MAX_ITERATIONS = 8;

    public AgentRuntimeConfig {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than zero");
        }
    }

    public static AgentRuntimeConfig defaults() {
        return new AgentRuntimeConfig(DEFAULT_MAX_ITERATIONS);
    }
}
