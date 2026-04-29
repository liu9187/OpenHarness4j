package io.openharness4j.runtime;

public record RetryPolicy(int maxAttempts, long backoffMillis) {

    public RetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        if (backoffMillis < 0) {
            throw new IllegalArgumentException("backoffMillis must be greater than or equal to zero");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, 0);
    }

    public static RetryPolicy fixedDelay(int maxAttempts, long backoffMillis) {
        return new RetryPolicy(maxAttempts, backoffMillis);
    }

    public boolean canRetryAfter(int failedAttempt) {
        return failedAttempt < maxAttempts;
    }
}
