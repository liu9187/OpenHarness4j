package io.openharness4j.starter;

import io.openharness4j.runtime.AgentRuntimeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openharness")
public class OpenHarnessProperties {

    private final Agent agent = new Agent();
    private final Memory memory = new Memory();

    public Agent getAgent() {
        return agent;
    }

    public Memory getMemory() {
        return memory;
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

    public static class Memory {
        private boolean enabled = true;
        private int maxMessages = 20;
        private boolean summarizeOverflow = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        public boolean isSummarizeOverflow() {
            return summarizeOverflow;
        }

        public void setSummarizeOverflow(boolean summarizeOverflow) {
            this.summarizeOverflow = summarizeOverflow;
        }
    }
}
