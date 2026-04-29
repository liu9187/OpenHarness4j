package io.openharness4j.springai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.memory.MemoryRetriever;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "io.openharness4j.starter.OpenHarnessAutoConfiguration")
@EnableConfigurationProperties(OpenHarnessSpringAiProperties.class)
public class SpringAiOpenHarnessAutoConfiguration {

    @Bean
    @ConditionalOnClass(ChatModel.class)
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(LLMAdapter.class)
    @ConditionalOnProperty(prefix = "openharness.spring-ai.model", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LLMAdapter springAiModelDriver(ChatModel chatModel, ObjectProvider<ObjectMapper> objectMapper) {
        return new SpringAiModelDriver(chatModel, objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnClass(VectorStore.class)
    @ConditionalOnBean(VectorStore.class)
    @ConditionalOnMissingBean(MemoryRetriever.class)
    @ConditionalOnProperty(prefix = "openharness.spring-ai.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MemoryRetriever springAiVectorStore(
            VectorStore vectorStore,
            OpenHarnessSpringAiProperties properties
    ) {
        return new SpringAiVectorStore(vectorStore, properties.getVector().getNamespace());
    }
}
