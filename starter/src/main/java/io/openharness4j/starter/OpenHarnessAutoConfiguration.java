package io.openharness4j.starter;

import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.LLMProviderProfile;
import io.openharness4j.llm.LLMProviderProfileFactory;
import io.openharness4j.llm.InMemoryLLMAdapterRegistry;
import io.openharness4j.llm.LLMAdapterRegistry;
import io.openharness4j.llm.NamedLLMAdapter;
import io.openharness4j.memory.ContextFileContextManager;
import io.openharness4j.memory.InMemoryMemoryStore;
import io.openharness4j.memory.MemoryContextManager;
import io.openharness4j.memory.MemorySessionManager;
import io.openharness4j.memory.MemoryStore;
import io.openharness4j.memory.MemorySummarizer;
import io.openharness4j.memory.MemoryWindowPolicy;
import io.openharness4j.memory.MemoryRetriever;
import io.openharness4j.memory.RetrievalAugmentedContextManager;
import io.openharness4j.memory.SimpleMemorySummarizer;
import io.openharness4j.multiagent.ConflictResolver;
import io.openharness4j.multiagent.DefaultMultiAgentAggregator;
import io.openharness4j.multiagent.DefaultMultiAgentRuntime;
import io.openharness4j.multiagent.DefaultPlanningAgent;
import io.openharness4j.multiagent.InMemorySubAgentRegistry;
import io.openharness4j.multiagent.KeyValueConflictResolver;
import io.openharness4j.multiagent.MultiAgentAggregator;
import io.openharness4j.multiagent.MultiAgentRuntime;
import io.openharness4j.multiagent.PlanningAgent;
import io.openharness4j.multiagent.SubAgentDefinition;
import io.openharness4j.multiagent.SubAgentRegistry;
import io.openharness4j.observability.AgentTracer;
import io.openharness4j.observability.ExportingAgentTracer;
import io.openharness4j.observability.InMemoryObservationExporter;
import io.openharness4j.observability.ObservationExporter;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.AuditingPermissionChecker;
import io.openharness4j.permission.CommandPermissionPolicy;
import io.openharness4j.permission.CommandPermissionRule;
import io.openharness4j.permission.InMemoryPermissionAuditStore;
import io.openharness4j.permission.NoopToolExecutionHook;
import io.openharness4j.permission.PathAccessMode;
import io.openharness4j.permission.PathAccessPolicy;
import io.openharness4j.permission.PathAccessRule;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.permission.PermissionAuditStore;
import io.openharness4j.permission.PermissionPolicy;
import io.openharness4j.permission.PolicyPermissionChecker;
import io.openharness4j.permission.ToolExecutionHook;
import io.openharness4j.permission.ToolPermissionRule;
import io.openharness4j.api.RiskLevel;
import io.openharness4j.plugin.InMemoryPluginRegistry;
import io.openharness4j.plugin.OpenHarnessPlugin;
import io.openharness4j.plugin.PluginContext;
import io.openharness4j.plugin.PluginManager;
import io.openharness4j.plugin.PluginRegistry;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.CostEstimator;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.runtime.RetryPolicy;
import io.openharness4j.skill.DefaultSkillExecutor;
import io.openharness4j.skill.InMemorySkillRegistry;
import io.openharness4j.skill.SkillDefinition;
import io.openharness4j.skill.SkillExecutor;
import io.openharness4j.skill.SkillRegistry;
import io.openharness4j.skill.YamlSkillLoader;
import io.openharness4j.skill.MarkdownSkillLoader;
import io.openharness4j.task.InMemoryTaskEngine;
import io.openharness4j.task.InMemoryTaskRegistry;
import io.openharness4j.task.TaskEngine;
import io.openharness4j.task.TaskHandler;
import io.openharness4j.task.TaskRegistry;
import io.openharness4j.toolkit.FileTool;
import io.openharness4j.toolkit.McpClient;
import io.openharness4j.toolkit.McpClientTool;
import io.openharness4j.toolkit.SearchProvider;
import io.openharness4j.toolkit.SearchTool;
import io.openharness4j.toolkit.ShellTool;
import io.openharness4j.toolkit.WebFetchTool;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(OpenHarnessProperties.class)
public class OpenHarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(ObjectProvider<Tool> tools) {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        tools.orderedStream().forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionAuditStore permissionAuditStore() {
        return new InMemoryPermissionAuditStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionChecker permissionChecker(OpenHarnessProperties properties, PermissionAuditStore permissionAuditStore) {
        return new AuditingPermissionChecker(permissionPolicyChecker(properties), permissionAuditStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutionHook toolExecutionHook() {
        return new NoopToolExecutionHook();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationExporter observationExporter() {
        return new InMemoryObservationExporter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentTracer agentTracer(ObservationExporter observationExporter) {
        return new ExportingAgentTracer(observationExporter);
    }

    @Bean
    @ConditionalOnMissingBean
    public LLMAdapterRegistry llmAdapterRegistry(ObjectProvider<NamedLLMAdapter> namedAdapters, OpenHarnessProperties properties) {
        InMemoryLLMAdapterRegistry registry = new InMemoryLLMAdapterRegistry();
        namedAdapters.orderedStream().forEach(adapter -> registry.register(adapter.name(), adapter.adapter()));
        if (properties.getProvider().isEnabled()) {
            InMemoryLLMAdapterRegistry providerRegistry = new LLMProviderProfileFactory()
                    .registry(providerProfiles(properties));
            providerRegistry.names().forEach(name -> providerRegistry.get(name).ifPresent(adapter -> registry.register(name, adapter)));
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(LLMAdapter.class)
    @ConditionalOnProperty(prefix = "openharness.provider", name = "enabled", havingValue = "true")
    public LLMAdapter configuredLlmAdapter(OpenHarnessProperties properties) {
        return new LLMProviderProfileFactory()
                .adapter(
                        providerProfiles(properties),
                        properties.getProvider().getDefaultProfile(),
                        properties.getProvider().getFallbackOrder()
                )
                .orElseThrow(() -> new IllegalArgumentException("no enabled provider profiles configured"));
    }

    @Bean
    @ConditionalOnMissingBean(name = "openHarnessFileTool")
    @ConditionalOnProperty(prefix = "openharness.toolkit.file", name = "enabled", havingValue = "true")
    public Tool openHarnessFileTool(OpenHarnessProperties properties) {
        OpenHarnessProperties.Toolkit toolkit = properties.getToolkit();
        Path baseDirectory = Path.of(toolkit.getBaseDirectory());
        List<PathAccessRule> rules = new ArrayList<>();
        toolkit.getFile().getDeniedPaths().forEach(path -> rules.add(PathAccessRule.deny(
                baseDirectory.resolve(path),
                EnumSet.allOf(PathAccessMode.class),
                RiskLevel.HIGH,
                "path is denied by configured policy: " + path
        )));
        toolkit.getFile().getAllowedPaths().forEach(path -> rules.add(PathAccessRule.allow(
                baseDirectory.resolve(path),
                EnumSet.allOf(PathAccessMode.class)
        )));
        return new FileTool(baseDirectory, PathAccessPolicy.denyByDefault(rules));
    }

    @Bean
    @ConditionalOnMissingBean(name = "openHarnessShellTool")
    @ConditionalOnProperty(prefix = "openharness.toolkit.shell", name = "enabled", havingValue = "true")
    public Tool openHarnessShellTool(OpenHarnessProperties properties) {
        OpenHarnessProperties.Toolkit toolkit = properties.getToolkit();
        List<CommandPermissionRule> rules = new ArrayList<>();
        toolkit.getShell().getDeniedContains().forEach(text -> rules.add(CommandPermissionRule.denyContains(
                text,
                RiskLevel.HIGH,
                "command is denied by configured policy: " + text
        )));
        toolkit.getShell().getAllowedPrefixes().forEach(prefix -> rules.add(CommandPermissionRule.allowPrefix(prefix)));
        return new ShellTool(
                Path.of(toolkit.getBaseDirectory()),
                CommandPermissionPolicy.denyByDefault(rules),
                List.of("/bin/sh", "-c"),
                toolkit.getShell().getDefaultTimeoutMillis()
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "openHarnessWebFetchTool")
    @ConditionalOnProperty(prefix = "openharness.toolkit.web-fetch", name = "enabled", havingValue = "true")
    public Tool openHarnessWebFetchTool() {
        return new WebFetchTool();
    }

    @Bean
    @ConditionalOnMissingBean(name = "openHarnessSearchTool")
    @ConditionalOnProperty(prefix = "openharness.toolkit.search", name = "enabled", havingValue = "true")
    public Tool openHarnessSearchTool(ObjectProvider<SearchProvider> searchProvider) {
        return new SearchTool(searchProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(name = "openHarnessMcpClientTool")
    @ConditionalOnProperty(prefix = "openharness.toolkit.mcp", name = "enabled", havingValue = "true")
    public Tool openHarnessMcpClientTool(ObjectProvider<McpClient> mcpClient) {
        return new McpClientTool(mcpClient.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openharness.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MemoryStore memoryStore() {
        return new InMemoryMemoryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemorySummarizer memorySummarizer() {
        return new SimpleMemorySummarizer();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemorySessionManager memorySessionManager(ObjectProvider<MemoryStore> memoryStore) {
        return new MemorySessionManager(memoryStore.getIfAvailable(InMemoryMemoryStore::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextManager contextManager(
            OpenHarnessProperties properties,
            ObjectProvider<MemoryStore> memoryStore,
            ObjectProvider<MemorySummarizer> memorySummarizer,
            ObjectProvider<MemoryRetriever> memoryRetriever
    ) {
        ContextManager delegate;
        MemoryStore store = memoryStore.getIfAvailable();
        OpenHarnessProperties.Memory memory = properties.getMemory();
        MemorySummarizer summarizer = memorySummarizer.getIfAvailable(SimpleMemorySummarizer::new);
        if (!properties.getMemory().isEnabled() || store == null) {
            delegate = new DefaultContextManager();
        } else {
            delegate = new MemoryContextManager(
                    store,
                    new MemoryWindowPolicy(
                            memory.getMaxMessages(),
                            memory.isSummarizeOverflow(),
                            summarizer
                    )
            );
        }
        if (!memory.getContextFiles().isEnabled()) {
            return retrievalAugmentedContextManager(delegate, properties, memoryRetriever);
        }
        OpenHarnessProperties.Memory.ContextFiles contextFiles = memory.getContextFiles();
        ContextManager contextFileDelegate = new ContextFileContextManager(
                delegate,
                Path.of(contextFiles.getBaseDirectory()),
                contextFiles.isLoadClaude(),
                contextFiles.isLoadMemory(),
                contextFiles.isPersistMemory(),
                summarizer
        );
        return retrievalAugmentedContextManager(contextFileDelegate, properties, memoryRetriever);
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(
            ObjectProvider<SkillDefinition> skillDefinitions,
            OpenHarnessProperties properties
    ) throws IOException {
        InMemorySkillRegistry registry = new InMemorySkillRegistry();
        skillDefinitions.orderedStream().forEach(registry::register);
        if (properties.getSkill().isEnabled()) {
            loadYamlSkills(registry, properties.getSkill().getYamlLocations());
            loadMarkdownSkills(registry, properties.getSkill().getMarkdownLocations());
        }
        return registry;
    }

    @Bean
    @ConditionalOnBean(LLMAdapter.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openharness.skill", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SkillExecutor skillExecutor(
            SkillRegistry skillRegistry,
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            ContextManager contextManager,
            OpenHarnessProperties properties,
            ObjectProvider<CostEstimator> costEstimator,
            ToolExecutionHook toolExecutionHook
    ) {
        return new DefaultSkillExecutor(
                skillRegistry,
                llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                contextManager,
                agentRuntimeConfig(properties, costEstimator.getIfAvailable(CostEstimator::none)),
                toolExecutionHook
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskRegistry taskRegistry(ObjectProvider<TaskHandler> taskHandlers) {
        InMemoryTaskRegistry registry = new InMemoryTaskRegistry();
        taskHandlers.orderedStream().forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openharness.task", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TaskEngine taskEngine(TaskRegistry taskRegistry, OpenHarnessProperties properties) {
        OpenHarnessProperties.Task task = properties.getTask();
        return new InMemoryTaskEngine(taskRegistry, task.getDefaultTimeoutMillis(), task.getPoolSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public SubAgentRegistry subAgentRegistry(ObjectProvider<SubAgentDefinition> subAgents) {
        InMemorySubAgentRegistry registry = new InMemorySubAgentRegistry();
        subAgents.orderedStream().forEach(registry::register);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public PlanningAgent planningAgent() {
        return new DefaultPlanningAgent();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConflictResolver conflictResolver() {
        return new KeyValueConflictResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiAgentAggregator multiAgentAggregator() {
        return new DefaultMultiAgentAggregator();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openharness.multi-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MultiAgentRuntime multiAgentRuntime(
            SubAgentRegistry subAgentRegistry,
            PlanningAgent planningAgent,
            ConflictResolver conflictResolver,
            MultiAgentAggregator multiAgentAggregator
    ) {
        return new DefaultMultiAgentRuntime(subAgentRegistry, planningAgent, conflictResolver, multiAgentAggregator);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistry pluginRegistry() {
        return new InMemoryPluginRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginContext pluginContext(
            ToolRegistry toolRegistry,
            SkillRegistry skillRegistry,
            TaskRegistry taskRegistry,
            SubAgentRegistry subAgentRegistry
    ) {
        return new PluginContext(toolRegistry, skillRegistry, taskRegistry, subAgentRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openharness.plugin", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PluginManager pluginManager(
            PluginRegistry pluginRegistry,
            PluginContext pluginContext,
            ObjectProvider<OpenHarnessPlugin> plugins
    ) {
        PluginManager manager = new PluginManager(pluginRegistry, pluginContext, plugins.orderedStream().toList());
        manager.activateAll();
        return manager;
    }

    @Bean
    @ConditionalOnBean(LLMAdapter.class)
    @ConditionalOnMissingBean
    public AgentRuntime agentRuntime(
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            ContextManager contextManager,
            OpenHarnessProperties properties,
            ObjectProvider<CostEstimator> costEstimator,
            ToolExecutionHook toolExecutionHook
    ) {
        return new DefaultAgentRuntime(
                llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                contextManager,
                agentRuntimeConfig(properties, costEstimator.getIfAvailable(CostEstimator::none)),
                toolExecutionHook
        );
    }

    private static void loadYamlSkills(InMemorySkillRegistry registry, Iterable<String> locations) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        YamlSkillLoader loader = new YamlSkillLoader();
        for (String location : locations) {
            Resource[] resources = resolver.getResources(location);
            for (Resource resource : resources) {
                try (var inputStream = resource.getInputStream()) {
                    registry.register(loader.load(inputStream));
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException("failed to load skill yaml: " + resource.getDescription(), ex);
                }
            }
        }
    }

    private static void loadMarkdownSkills(InMemorySkillRegistry registry, Iterable<String> locations) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MarkdownSkillLoader loader = new MarkdownSkillLoader();
        for (String location : locations) {
            Resource[] resources = resolver.getResources(location);
            for (Resource resource : resources) {
                try (var inputStream = resource.getInputStream()) {
                    registry.register(loader.load(inputStream, resource.getFilename()));
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException("failed to load skill markdown: " + resource.getDescription(), ex);
                }
            }
        }
    }

    private static PermissionChecker permissionPolicyChecker(OpenHarnessProperties properties) {
        OpenHarnessProperties.Permission permission = properties.getPermission();
        List<ToolPermissionRule> rules = new ArrayList<>();
        permission.getAllowedTools().forEach(tool -> rules.add(ToolPermissionRule.allow(tool)));
        permission.getDeniedTools().forEach(tool -> rules.add(ToolPermissionRule.deny(
                tool,
                RiskLevel.HIGH,
                "tool is denied by configured policy: " + tool
        )));
        if (rules.isEmpty() && permission.isDefaultAllow()) {
            return new AllowAllPermissionChecker();
        }
        return new PolicyPermissionChecker(new PermissionPolicy(permission.isDefaultAllow(), rules));
    }

    private static AgentRuntimeConfig agentRuntimeConfig(OpenHarnessProperties properties, CostEstimator costEstimator) {
        OpenHarnessProperties.Agent agent = properties.getAgent();
        return new AgentRuntimeConfig(
                agent.getMaxIterations(),
                agent.isParallelToolExecution(),
                RetryPolicy.fixedDelay(agent.getLlmRetryMaxAttempts(), agent.getLlmRetryBackoffMillis()),
                RetryPolicy.fixedDelay(agent.getToolRetryMaxAttempts(), agent.getToolRetryBackoffMillis()),
                costEstimator
        );
    }

    private static ContextManager retrievalAugmentedContextManager(
            ContextManager delegate,
            OpenHarnessProperties properties,
            ObjectProvider<MemoryRetriever> memoryRetriever
    ) {
        OpenHarnessProperties.Memory.Retrieval retrieval = properties.getMemory().getRetrieval();
        MemoryRetriever retriever = memoryRetriever.getIfAvailable();
        if (!retrieval.isEnabled() || retriever == null) {
            return delegate;
        }
        return new RetrievalAugmentedContextManager(
                delegate,
                retriever,
                retrieval.getNamespace(),
                retrieval.getTopK(),
                retrieval.getSimilarityThreshold(),
                retrieval.isIndexCompletedMessages()
        );
    }

    private static List<LLMProviderProfile> providerProfiles(OpenHarnessProperties properties) {
        return properties.getProvider().getProfiles().stream()
                .map(profile -> new LLMProviderProfile(
                        profile.getName(),
                        profile.getEndpoint(),
                        profile.getApiKey(),
                        profile.getApiKeyEnv(),
                        profile.getModel(),
                        profile.getModelEnv(),
                        profile.isEnabled()
                ))
                .toList();
    }
}
