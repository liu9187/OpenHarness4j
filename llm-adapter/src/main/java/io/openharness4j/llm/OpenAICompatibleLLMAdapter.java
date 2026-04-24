package io.openharness4j.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.api.Usage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAICompatibleLLMAdapter implements LLMAdapter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    public OpenAICompatibleLLMAdapter(String endpoint, String apiKey, String model) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), endpoint, apiKey, model);
    }

    public OpenAICompatibleLLMAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey,
            String model
    ) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools) {
        try {
            String payload = objectMapper.writeValueAsString(toRequestBody(messages, tools));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LLMAdapterException("LLM request failed with status " + response.statusCode() + ": " + response.body());
            }
            return parseResponse(response.body());
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to serialize LLM request", ex);
        } catch (IOException ex) {
            throw new LLMAdapterException("failed to call LLM endpoint", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LLMAdapterException("LLM request was interrupted", ex);
        }
    }

    LLMResponse parseResponse(String body) {
        try {
            Map<String, Object> root = objectMapper.readValue(body, MAP_TYPE);
            List<Map<String, Object>> choices = listOfMaps(root.get("choices"));
            if (choices.isEmpty()) {
                throw new LLMAdapterException("LLM response contains no choices");
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> messageNode = map(firstChoice.get("message"));
            String content = stringValue(messageNode.get("content"));
            List<ToolCall> toolCalls = parseToolCalls(messageNode.get("tool_calls"));

            Usage usage = parseUsage(root.get("usage"));
            return new LLMResponse(Message.assistant(content, toolCalls), toolCalls, usage);
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to parse LLM response", ex);
        }
    }

    private Map<String, Object> toRequestBody(List<Message> messages, List<ToolDefinition> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", toMessagePayloads(messages));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", toToolPayloads(tools));
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private List<Map<String, Object>> toMessagePayloads(List<Message> messages) {
        if (messages == null) {
            return List.of();
        }
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("role", message.role().name().toLowerCase());
            payload.put("content", message.content());
            if (message.toolCallId() != null) {
                payload.put("tool_call_id", message.toolCallId());
            }
            if (message.name() != null) {
                payload.put("name", message.name());
            }
            if (!message.toolCalls().isEmpty()) {
                payload.put("tool_calls", toToolCallPayloads(message.toolCalls()));
            }
            payloads.add(payload);
        }
        return payloads;
    }

    private List<Map<String, Object>> toToolPayloads(List<ToolDefinition> tools) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parametersSchema());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "function");
            payload.put("function", function);
            payloads.add(payload);
        }
        return payloads;
    }

    private List<Map<String, Object>> toToolCallPayloads(List<ToolCall> toolCalls) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (ToolCall call : toolCalls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.name());
            function.put("arguments", writeJson(call.args()));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", call.id());
            payload.put("type", "function");
            payload.put("function", function);
            payloads.add(payload);
        }
        return payloads;
    }

    private List<ToolCall> parseToolCalls(Object value) {
        List<Map<String, Object>> toolCallNodes = listOfMaps(value);
        List<ToolCall> toolCalls = new ArrayList<>();
        for (Map<String, Object> node : toolCallNodes) {
            Map<String, Object> function = map(node.get("function"));
            toolCalls.add(new ToolCall(
                    stringValue(node.get("id")),
                    stringValue(function.get("name")),
                    parseArguments(function.get("arguments"))
            ));
        }
        return toolCalls;
    }

    private Map<String, Object> parseArguments(Object value) {
        if (!(value instanceof String arguments) || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to parse tool call arguments", ex);
        }
    }

    private Usage parseUsage(Object value) {
        Map<String, Object> usage = map(value);
        return new Usage(
                longValue(usage.get("prompt_tokens")),
                longValue(usage.get("completion_tokens")),
                longValue(usage.get("total_tokens"))
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to serialize tool call arguments", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return 0;
    }
}
