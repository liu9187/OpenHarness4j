package io.openharness4j.api;

public record Usage(
        long promptTokens,
        long completionTokens,
        long totalTokens
) {
    public Usage {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token values must be greater than or equal to zero");
        }
    }

    public static Usage zero() {
        return new Usage(0, 0, 0);
    }

    public Usage plus(Usage other) {
        if (other == null) {
            return this;
        }
        return new Usage(
                promptTokens + other.promptTokens(),
                completionTokens + other.completionTokens(),
                totalTokens + other.totalTokens()
        );
    }
}
