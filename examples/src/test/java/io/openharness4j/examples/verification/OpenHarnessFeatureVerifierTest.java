package io.openharness4j.examples.verification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenHarnessFeatureVerifierTest {

    @Test
    void verifiesAllExampleScenarios() {
        List<VerificationResult> results = OpenHarnessFeatureVerifier.runAll();

        assertEquals(11, results.size());
        assertFalse(results.stream().anyMatch(result -> result.traceId().isBlank()));
    }
}
