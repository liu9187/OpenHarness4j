package io.openharness4j.runtime;

import io.openharness4j.api.Cost;
import io.openharness4j.api.Usage;

@FunctionalInterface
public interface CostEstimator {

    Cost estimate(Usage usage);

    static CostEstimator none() {
        return usage -> Cost.zero();
    }
}
