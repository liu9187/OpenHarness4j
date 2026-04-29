package io.openharness4j.springai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.Usage;
import io.openharness4j.llm.LLMAdapter;
import io.openharness4j.llm.LLMAdapterException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SpringAiModelDriver implements LLMAdapter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public SpringAiModelDriver(ChatModel chatModel) {
        this(chatModel, new ObjectMapper());
    }

    public SpringAiModelDriver(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools) {
        ChatResponse response = chatModel.call(new Prompt(toSpringMessages(messages), chatOptions(tools)));
        return toOpenHarnessResponse(response);
    }

    private ToolCallingChatOptions chatOptions(List<ToolDefinition> tools) {
        ToolCallingChatOptions.Builder builder = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false);
        List<ToolCallback> callbacks = toToolCallbacks(tools);
        if (!callbacks.isEmpty()) {
            builder.toolCallbacks(callbacks);
        }
        return builder.build();
    }

    private List<ToolCallback> toToolCallbacks(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            callbacks.add(new OpenHarnessToolCallback(tool, objectMapper));
        }
        return callbacks;
    }

    private List<org.springframework.ai.chat.messages.Message> toSpringMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<org.springframework.ai.chat.messages.Message> springMessages = new ArrayList<>();
        for (Message message : messages) {
            springMessages.add(toSpringMessage(message));
        }
        return List.copyOf(springMessages);
    }

    private org.springframework.ai.chat.messages.Message toSpringMessage(Message message) {
        if (message.role() == MessageRole.SYSTEM) {
            return new SystemMessage(message.content());
        }
        if (message.role() == MessageRole.USER) {
            return new UserMessage(message.content());
        }
        if (message.role() == MessageRole.TOOL) {
            return ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            message.toolCallId(),
                            message.name(),
                            message.content()
                    )))
                    .build();
        }
        List<AssistantMessage.ToolCall> toolCalls = message.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(
                        call.id(),
                        "function",
                        call.name(),
                        writeJson(call.args())
                ))
                .toList();
        if (toolCalls.isEmpty()) {
            return new AssistantMessage(message.content());
        }
        return AssistantMessage.builder()
                .content(message.content())
                .toolCalls(toolCalls)
                .build();
    }

    private LLMResponse toOpenHarnessResponse(ChatResponse response) {
        if (response == null || response.getResults().isEmpty()) {
            return new LLMResponse(null, List.of(), Usage.zero());
        }
        Generation generation = response.getResult();
        AssistantMessage assistantMessage = generation.getOutput();
        String content = assistantMessage == null ? "" : assistantMessage.getText();
        List<ToolCall> toolCalls = assistantMessage == null ? List.of() : assistantMessage.getToolCalls().stream()
                .map(call -> new ToolCall(call.id(), call.name(), parseArguments(call.arguments())))
                .toList();
        return new LLMResponse(Message.assistant(content, toolCalls), toolCalls, usage(response));
    }

    private Usage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return Usage.zero();
        }
        org.springframework.ai.chat.metadata.Usage usage = response.getMetadata().getUsage();
        return new Usage(
                longValue(usage.getPromptTokens()),
                longValue(usage.getCompletionTokens()),
                longValue(usage.getTotalTokens())
        );
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to parse Spring AI tool call arguments", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to serialize Spring AI tool schema or arguments", ex);
        }
    }

    private static long longValue(Integer value) {
        return value == null ? 0 : value.longValue();
    }
}
