package io.openharness4j.springai;

import io.openharness4j.api.AgentRequest;
import io.openharness4j.api.AgentResponse;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.memory.MemoryRetriever;
import io.openharness4j.memory.RetrievalAugmentedContextManager;
import io.openharness4j.runtime.AgentRuntime;
import io.openharness4j.runtime.ContextManager;
import io.openharness4j.runtime.DefaultContextManager;
import io.openharness4j.starter.OpenHarnessAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiOpenHarnessAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SpringAiOpenHarnessAutoConfiguration.class,
                    OpenHarnessAutoConfiguration.class
            ));

    @Test
    void createsLlmAdapterAndRuntimeFromChatModel() {
        contextRunner
                .withUserConfiguration(ChatModelConfiguration.class)
                .run(context -> {
                    assertTrue(context.getBean(LLMAdapter.class) instanceof SpringAiModelDriver);
                    assertTrue(context.containsBean("agentRuntime"));

                    AgentResponse response = context.getBean(AgentRuntime.class)
                            .run(AgentRequest.of("s1", "u1", "hello"));

                    assertEquals("spring ai auto ok", response.content());
                });
    }

    @Test
    void createsRetrieverAndWrapsContextManagerWhenRetrievalEnabled() {
        contextRunner
                .withUserConfiguration(VectorStoreConfiguration.class)
                .withPropertyValues("openharness.memory.retrieval.enabled=true")
                .run(context -> {
                    assertTrue(context.getBean(MemoryRetriever.class) instanceof SpringAiVectorStore);
                    assertTrue(context.getBean(ContextManager.class) instanceof RetrievalAugmentedContextManager);
                });
    }

    @Test
    void backsOffWhenCustomLlmAdapterExists() {
        contextRunner
                .withUserConfiguration(ChatModelConfiguration.class, CustomLlmAdapterConfiguration.class)
                .run(context -> {
                    LLMAdapter adapter = context.getBean(LLMAdapter.class);

                    assertFalse(adapter instanceof SpringAiModelDriver);
                    assertEquals("custom", adapter.chat(List.of(), List.of()).message().content());
                });
    }

    @Test
    void starterBacksOffWhenCustomContextManagerExists() {
        contextRunner
                .withUserConfiguration(VectorStoreConfiguration.class, CustomContextManagerConfiguration.class)
                .withPropertyValues("openharness.memory.retrieval.enabled=true")
                .run(context -> assertTrue(context.getBean(ContextManager.class) instanceof CustomContextManager));
    }

    static class ChatModelConfiguration {
        @Bean
        ChatModel chatModel() {
            return new FixedChatModel("spring ai auto ok");
        }
    }

    static class VectorStoreConfiguration {
        @Bean
        VectorStore vectorStore() {
            return new VectorStore() {
                @Override
                public void add(List<Document> documents) {
                }

                @Override
                public void delete(List<String> idList) {
                }

                @Override
                public void delete(Filter.Expression filterExpression) {
                }

                @Override
                public List<Document> similaritySearch(SearchRequest request) {
                    return List.of();
                }
            };
        }
    }

    static class CustomLlmAdapterConfiguration {
        @Bean
        LLMAdapter customLlmAdapter() {
            return (messages, tools) -> LLMResponse.text("custom");
        }
    }

    static class CustomContextManagerConfiguration {
        @Bean
        ContextManager contextManager() {
            return new CustomContextManager();
        }
    }

    private static final class CustomContextManager extends DefaultContextManager {
    }

    private static final class FixedChatModel implements ChatModel {
        private final String content;

        private FixedChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }
}
