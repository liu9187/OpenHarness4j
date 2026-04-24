package io.openharness4j.starter;

import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.observability.AgentTracer;
import io.openharness4j.observability.DefaultAgentTracer;
import io.openharness4j.permission.AllowAllPermissionChecker;
import io.openharness4j.permission.PermissionChecker;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.AgentRuntimeConfig;
import io.openharness4j.runtime.DefaultAgentRuntime;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(OpenHarnessProperties.class)
public class OpenHarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new InMemoryToolRegistry();
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
    @ConditionalOnBean(LLMAdapter.class)
    @ConditionalOnMissingBean
    public AgentRuntime agentRuntime(
            LLMAdapter llmAdapter,
            ToolRegistry toolRegistry,
            PermissionChecker permissionChecker,
            AgentTracer agentTracer,
            OpenHarnessProperties properties
    ) {
        return new DefaultAgentRuntime(
                llmAdapter,
                toolRegistry,
                permissionChecker,
                agentTracer,
                new DefaultContextManager(),
                new AgentRuntimeConfig(properties.getAgent().getMaxIterations())
        );
    }
}
