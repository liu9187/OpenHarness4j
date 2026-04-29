package io.openharness4j.springai;

import io.openharness4j.memory.MemoryRetrievalRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openharness.spring-ai")
public class OpenHarnessSpringAiProperties {

    private final Model model = new Model();
    private final Vector vector = new Vector();

    public Model getModel() {
        return model;
    }

    public Vector getVector() {
        return vector;
    }

    public static class Model {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Vector {
        private boolean enabled = true;
        private String namespace = MemoryRetrievalRequest.DEFAULT_NAMESPACE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace == null || namespace.isBlank()
                    ? MemoryRetrievalRequest.DEFAULT_NAMESPACE
                    : namespace;
        }
    }
}
