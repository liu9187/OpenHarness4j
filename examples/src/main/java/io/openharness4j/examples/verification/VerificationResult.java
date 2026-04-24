package io.openharness4j.examples.verification;

import io.openharness4j.api.FinishReason;

public record VerificationResult(
        String name,
        FinishReason finishReason,
        int toolCallCount,
        long totalTokens,
        String traceId,
        String detail
) {
}
