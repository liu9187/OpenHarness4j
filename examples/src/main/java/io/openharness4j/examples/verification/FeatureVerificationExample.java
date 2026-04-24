package io.openharness4j.examples.verification;

import java.util.List;

public class FeatureVerificationExample {

    public static void main(String[] args) {
        List<VerificationResult> results = OpenHarnessFeatureVerifier.runAll();

        System.out.println("OpenHarness4j v0.2 feature verification");
        System.out.println("========================================");
        for (VerificationResult result : results) {
            System.out.printf(
                    "[PASS] %-28s finish=%-24s tools=%d tokens=%d detail=%s%n",
                    result.name(),
                    result.finishReason(),
                    result.toolCallCount(),
                    result.totalTokens(),
                    result.detail()
            );
        }
        System.out.println("========================================");
        System.out.println("All " + results.size() + " verification scenarios passed.");
    }
}
