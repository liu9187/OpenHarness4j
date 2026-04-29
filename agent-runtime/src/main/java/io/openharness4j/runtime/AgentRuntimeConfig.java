package io.openharness4j.runtime;

public record AgentRuntimeConfig(
        int maxIterations,
        boolean parallelToolExecution,
        RetryPolicy llmRetryPolicy,
        RetryPolicy toolRetryPolicy,
        CostEstimator costEstimator
) {

    public static final int DEFAULT_MAX_ITERATIONS = 8;

    public AgentRuntimeConfig {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than zero");
        }
        llmRetryPolicy = llmRetryPolicy == null ? RetryPolicy.none() : llmRetryPolicy;
        toolRetryPolicy = toolRetryPolicy == null ? RetryPolicy.none() : toolRetryPolicy;
        costEstimator = costEstimator == null ? CostEstimator.none() : costEstimator;
    }

    public AgentRuntimeConfig(int maxIterations) {
        this(maxIterations, false, RetryPolicy.none(), RetryPolicy.none(), CostEstimator.none());
    }

    public static AgentRuntimeConfig defaults() {
        return new AgentRuntimeConfig(DEFAULT_MAX_ITERATIONS);
    }

    public AgentRuntimeConfig withMaxIterations(int maxIterations) {
        return new AgentRuntimeConfig(
                maxIterations,
                parallelToolExecution,
                llmRetryPolicy,
                toolRetryPolicy,
                costEstimator
        );
    }

    public AgentRuntimeConfig withParallelToolExecution(boolean parallelToolExecution) {
        return new AgentRuntimeConfig(
                maxIterations,
                parallelToolExecution,
                llmRetryPolicy,
                toolRetryPolicy,
                costEstimator
        );
    }

    public AgentRuntimeConfig withLlmRetryPolicy(RetryPolicy llmRetryPolicy) {
        return new AgentRuntimeConfig(
                maxIterations,
                parallelToolExecution,
                llmRetryPolicy,
                toolRetryPolicy,
                costEstimator
        );
    }

    public AgentRuntimeConfig withToolRetryPolicy(RetryPolicy toolRetryPolicy) {
        return new AgentRuntimeConfig(
                maxIterations,
                parallelToolExecution,
                llmRetryPolicy,
                toolRetryPolicy,
                costEstimator
        );
    }

    public AgentRuntimeConfig withCostEstimator(CostEstimator costEstimator) {
        return new AgentRuntimeConfig(
                maxIterations,
                parallelToolExecution,
                llmRetryPolicy,
                toolRetryPolicy,
                costEstimator
        );
    }
}
