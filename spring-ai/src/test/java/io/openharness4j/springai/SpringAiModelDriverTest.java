package io.openharness4j.springai;

import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiModelDriverTest {

    @Test
    void mapsTextResponse() {
        CapturingChatModel chatModel = new CapturingChatModel(new ChatResponse(List.of(
                new Generation(new AssistantMessage("spring ai ok"))
        )));

        LLMResponse response = new SpringAiModelDriver(chatModel).chat(List.of(Message.user("hello")), List.of());

        assertEquals("spring ai ok", response.message().content());
        assertFalse(response.hasToolCalls());
    }

    @Test
    void mapsToolCallResponseAndDisablesSpringAiInternalToolExecution() {
        CapturingChatModel chatModel = new CapturingChatModel(new ChatResponse(List.of(
                new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call_1",
                                "function",
                                "echo",
                                "{\"text\":\"hello\"}"
                        )))
                        .build())
        )));
        ToolDefinition definition = new ToolDefinition(
                "echo",
                "Echo text",
                Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string")))
        );

        LLMResponse response = new SpringAiModelDriver(chatModel).chat(List.of(Message.user("call echo")), List.of(definition));

        assertTrue(response.hasToolCalls());
        assertEquals("echo", response.effectiveToolCalls().get(0).name());
        assertEquals("hello", response.effectiveToolCalls().get(0).args().get("text"));

        ToolCallingChatOptions options = (ToolCallingChatOptions) chatModel.lastPrompt.getOptions();
        assertEquals(Boolean.FALSE, options.getInternalToolExecutionEnabled());
        assertEquals(1, options.getToolCallbacks().size());
        assertEquals("echo", options.getToolCallbacks().get(0).getToolDefinition().name());
    }

    private static final class CapturingChatModel implements ChatModel {
        private final ChatResponse response;
        private Prompt lastPrompt;

        private CapturingChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            return response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }
}
