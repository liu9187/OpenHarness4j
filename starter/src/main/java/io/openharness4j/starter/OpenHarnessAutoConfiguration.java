package io.openharness4j.starter;

import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.memory.InMemoryMemoryStore;
import io.openharness4j.memory.MemoryContextManager;
import io.openharness4j.memory.MemoryStore;
import io.openharness4j.memory.MemorySummarizer;
import io.openharness4j.memory.MemoryWindowPolicy;
import io.openharness4j.memory.SimpleMemorySummarizer;
import io.openharness4j.observability.AgentTracer;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.tool.ToolRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
    public PermissionChecker permissionChecker() {
        return new AllowAllPermissionChecker();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentTracer agentTracer() {
        return new DefaultAgentTracer();
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
    public ContextManager contextManager(
            OpenHarnessProperties properties,
            ObjectProvider<MemoryStore> memoryStore,
            ObjectProvider<MemorySummarizer> memorySummarizer
    ) {
        if (!properties.getMemory().isEnabled()) {
            return new DefaultContextManager();
        }
        MemoryStore store = memoryStore.getIfAvailable();
        if (store == null) {
            return new DefaultContextManager();
        }
        OpenHarnessProperties.Memory memory = properties.getMemory();
        return new MemoryContextManager(
                store,
                new MemoryWindowPolicy(
                        memory.getMaxMessages(),
                        memory.isSummarizeOverflow(),
                        memorySummarizer.getIfAvailable(SimpleMemorySummarizer::new)
                )
        );
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
            OpenHarnessProperties properties
    ) {
        return new DefaultAgentRuntime(
                llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                contextManager,
                new AgentRuntimeConfig(properties.getAgent().getMaxIterations())
        );
    }
}
