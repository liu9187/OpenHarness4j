package io.openharness4j.starter;

import io.openharness4j.runtime.AgentRuntimeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openharness")
public class OpenHarnessProperties {

    private final Agent agent = new Agent();

    public Agent getAgent() {
        return agent;
    }

    public static class Agent {
        private int maxIterations = AgentRuntimeConfig.DEFAULT_MAX_ITERATIONS;

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
    }
}
