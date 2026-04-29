package io.openharness4j.starter;

import io.openharness4j.memory.MemoryRetrievalRequest;
import io.openharness4j.runtime.AgentRuntimeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "openharness")
public class OpenHarnessProperties {

    private final Agent agent = new Agent();
    private final Memory memory = new Memory();
    private final Skill skill = new Skill();
    private final Task task = new Task();
    private final MultiAgent multiAgent = new MultiAgent();
    private final Permission permission = new Permission();
    private final Plugin plugin = new Plugin();
    private final Toolkit toolkit = new Toolkit();
    private final Provider provider = new Provider();

    public Agent getAgent() {
        return agent;
    }

    public Memory getMemory() {
        return memory;
    }

    public Skill getSkill() {
        return skill;
    }

    public Task getTask() {
        return task;
    }

    public MultiAgent getMultiAgent() {
        return multiAgent;
    }

    public Permission getPermission() {
        return permission;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Toolkit getToolkit() {
        return toolkit;
    }

    public Provider getProvider() {
        return provider;
    }

    public static class Agent {
        private int maxIterations = AgentRuntimeConfig.DEFAULT_MAX_ITERATIONS;
        private boolean parallelToolExecution = false;
        private int llmRetryMaxAttempts = 1;
        private long llmRetryBackoffMillis = 0;
        private int toolRetryMaxAttempts = 1;
        private long toolRetryBackoffMillis = 0;

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        public boolean isParallelToolExecution() {
            return parallelToolExecution;
        }

        public void setParallelToolExecution(boolean parallelToolExecution) {
            this.parallelToolExecution = parallelToolExecution;
        }

        public int getLlmRetryMaxAttempts() {
            return llmRetryMaxAttempts;
        }

        public void setLlmRetryMaxAttempts(int llmRetryMaxAttempts) {
            this.llmRetryMaxAttempts = llmRetryMaxAttempts;
        }

        public long getLlmRetryBackoffMillis() {
            return llmRetryBackoffMillis;
        }

        public void setLlmRetryBackoffMillis(long llmRetryBackoffMillis) {
            this.llmRetryBackoffMillis = llmRetryBackoffMillis;
        }

        public int getToolRetryMaxAttempts() {
            return toolRetryMaxAttempts;
        }

        public void setToolRetryMaxAttempts(int toolRetryMaxAttempts) {
            this.toolRetryMaxAttempts = toolRetryMaxAttempts;
        }

        public long getToolRetryBackoffMillis() {
            return toolRetryBackoffMillis;
        }

        public void setToolRetryBackoffMillis(long toolRetryBackoffMillis) {
            this.toolRetryBackoffMillis = toolRetryBackoffMillis;
        }
    }

    public static class Memory {
        private boolean enabled = true;
        private int maxMessages = 20;
        private boolean summarizeOverflow = true;
        private final ContextFiles contextFiles = new ContextFiles();
        private final Retrieval retrieval = new Retrieval();

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

        public ContextFiles getContextFiles() {
            return contextFiles;
        }

        public Retrieval getRetrieval() {
            return retrieval;
        }

        public static class ContextFiles {
            private boolean enabled = false;
            private String baseDirectory = ".";
            private boolean loadClaude = true;
            private boolean loadMemory = true;
            private boolean persistMemory = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getBaseDirectory() {
                return baseDirectory;
            }

            public void setBaseDirectory(String baseDirectory) {
                this.baseDirectory = baseDirectory == null || baseDirectory.isBlank() ? "." : baseDirectory;
            }

            public boolean isLoadClaude() {
                return loadClaude;
            }

            public void setLoadClaude(boolean loadClaude) {
                this.loadClaude = loadClaude;
            }

            public boolean isLoadMemory() {
                return loadMemory;
            }

            public void setLoadMemory(boolean loadMemory) {
                this.loadMemory = loadMemory;
            }

            public boolean isPersistMemory() {
                return persistMemory;
            }

            public void setPersistMemory(boolean persistMemory) {
                this.persistMemory = persistMemory;
            }
        }

        public static class Retrieval {
            private boolean enabled = false;
            private String namespace = MemoryRetrievalRequest.DEFAULT_NAMESPACE;
            private int topK = MemoryRetrievalRequest.DEFAULT_TOP_K;
            private double similarityThreshold = MemoryRetrievalRequest.DEFAULT_SIMILARITY_THRESHOLD;
            private boolean indexCompletedMessages = false;

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

            public int getTopK() {
                return topK;
            }

            public void setTopK(int topK) {
                this.topK = topK <= 0 ? MemoryRetrievalRequest.DEFAULT_TOP_K : topK;
            }

            public double getSimilarityThreshold() {
                return similarityThreshold;
            }

            public void setSimilarityThreshold(double similarityThreshold) {
                if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
                    this.similarityThreshold = MemoryRetrievalRequest.DEFAULT_SIMILARITY_THRESHOLD;
                    return;
                }
                this.similarityThreshold = similarityThreshold;
            }

            public boolean isIndexCompletedMessages() {
                return indexCompletedMessages;
            }

            public void setIndexCompletedMessages(boolean indexCompletedMessages) {
                this.indexCompletedMessages = indexCompletedMessages;
            }
        }
    }

    public static class Skill {
        private boolean enabled = true;
        private List<String> yamlLocations = new ArrayList<>(List.of(
                "classpath*:openharness/skills/*.yaml",
                "classpath*:openharness/skills/*.yml"
        ));
        private List<String> markdownLocations = new ArrayList<>(List.of(
                "classpath*:openharness/skills/*.md",
                "classpath*:openharness/skills/*/SKILL.md"
        ));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getYamlLocations() {
            return yamlLocations;
        }

        public void setYamlLocations(List<String> yamlLocations) {
            this.yamlLocations = yamlLocations == null ? new ArrayList<>() : new ArrayList<>(yamlLocations);
        }

        public List<String> getMarkdownLocations() {
            return markdownLocations;
        }

        public void setMarkdownLocations(List<String> markdownLocations) {
            this.markdownLocations = markdownLocations == null ? new ArrayList<>() : new ArrayList<>(markdownLocations);
        }
    }

    public static class Task {
        private boolean enabled = true;
        private long defaultTimeoutMillis = 0;
        private int poolSize = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDefaultTimeoutMillis() {
            return defaultTimeoutMillis;
        }

        public void setDefaultTimeoutMillis(long defaultTimeoutMillis) {
            this.defaultTimeoutMillis = defaultTimeoutMillis;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }

    public static class MultiAgent {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Permission {
        private boolean defaultAllow = true;
        private List<String> allowedTools = new ArrayList<>();
        private List<String> deniedTools = new ArrayList<>();

        public boolean isDefaultAllow() {
            return defaultAllow;
        }

        public void setDefaultAllow(boolean defaultAllow) {
            this.defaultAllow = defaultAllow;
        }

        public List<String> getAllowedTools() {
            return allowedTools;
        }

        public void setAllowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools == null ? new ArrayList<>() : new ArrayList<>(allowedTools);
        }

        public List<String> getDeniedTools() {
            return deniedTools;
        }

        public void setDeniedTools(List<String> deniedTools) {
            this.deniedTools = deniedTools == null ? new ArrayList<>() : new ArrayList<>(deniedTools);
        }
    }

    public static class Plugin {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Toolkit {
        private String baseDirectory = ".";
        private final File file = new File();
        private final Shell shell = new Shell();
        private final WebFetch webFetch = new WebFetch();
        private final Search search = new Search();
        private final Mcp mcp = new Mcp();

        public String getBaseDirectory() {
            return baseDirectory;
        }

        public void setBaseDirectory(String baseDirectory) {
            this.baseDirectory = baseDirectory == null || baseDirectory.isBlank() ? "." : baseDirectory;
        }

        public File getFile() {
            return file;
        }

        public Shell getShell() {
            return shell;
        }

        public WebFetch getWebFetch() {
            return webFetch;
        }

        public Search getSearch() {
            return search;
        }

        public Mcp getMcp() {
            return mcp;
        }

        public static class File {
            private boolean enabled = false;
            private List<String> allowedPaths = new ArrayList<>();
            private List<String> deniedPaths = new ArrayList<>();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<String> getAllowedPaths() {
                return allowedPaths;
            }

            public void setAllowedPaths(List<String> allowedPaths) {
                this.allowedPaths = allowedPaths == null ? new ArrayList<>() : new ArrayList<>(allowedPaths);
            }

            public List<String> getDeniedPaths() {
                return deniedPaths;
            }

            public void setDeniedPaths(List<String> deniedPaths) {
                this.deniedPaths = deniedPaths == null ? new ArrayList<>() : new ArrayList<>(deniedPaths);
            }
        }

        public static class Shell {
            private boolean enabled = false;
            private List<String> allowedPrefixes = new ArrayList<>();
            private List<String> deniedContains = new ArrayList<>();
            private long defaultTimeoutMillis = 10000;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public List<String> getAllowedPrefixes() {
                return allowedPrefixes;
            }

            public void setAllowedPrefixes(List<String> allowedPrefixes) {
                this.allowedPrefixes = allowedPrefixes == null ? new ArrayList<>() : new ArrayList<>(allowedPrefixes);
            }

            public List<String> getDeniedContains() {
                return deniedContains;
            }

            public void setDeniedContains(List<String> deniedContains) {
                this.deniedContains = deniedContains == null ? new ArrayList<>() : new ArrayList<>(deniedContains);
            }

            public long getDefaultTimeoutMillis() {
                return defaultTimeoutMillis;
            }

            public void setDefaultTimeoutMillis(long defaultTimeoutMillis) {
                this.defaultTimeoutMillis = defaultTimeoutMillis;
            }
        }

        public static class WebFetch {
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class Search {
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class Mcp {
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    public static class Provider {
        private boolean enabled = false;
        private String defaultProfile = "";
        private List<String> fallbackOrder = new ArrayList<>();
        private List<Profile> profiles = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultProfile() {
            return defaultProfile;
        }

        public void setDefaultProfile(String defaultProfile) {
            this.defaultProfile = defaultProfile == null ? "" : defaultProfile;
        }

        public List<String> getFallbackOrder() {
            return fallbackOrder;
        }

        public void setFallbackOrder(List<String> fallbackOrder) {
            this.fallbackOrder = fallbackOrder == null ? new ArrayList<>() : new ArrayList<>(fallbackOrder);
        }

        public List<Profile> getProfiles() {
            return profiles;
        }

        public void setProfiles(List<Profile> profiles) {
            this.profiles = profiles == null ? new ArrayList<>() : new ArrayList<>(profiles);
        }

        public static class Profile {
            private String name = "";
            private String endpoint = "";
            private String apiKey = "";
            private String apiKeyEnv = "";
            private String model = "";
            private String modelEnv = "";
            private boolean enabled = true;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name == null ? "" : name;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint == null ? "" : endpoint;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey == null ? "" : apiKey;
            }

            public String getApiKeyEnv() {
                return apiKeyEnv;
            }

            public void setApiKeyEnv(String apiKeyEnv) {
                this.apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model == null ? "" : model;
            }

            public String getModelEnv() {
                return modelEnv;
            }

            public void setModelEnv(String modelEnv) {
                this.modelEnv = modelEnv == null ? "" : modelEnv;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
