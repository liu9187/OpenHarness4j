package io.openharness4j.springai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openharness4j.api.ToolDefinition;
import io.openharness4j.llm.LLMAdapterException;
import org.springframework.ai.tool.ToolCallback;

import java.util.Objects;

final class OpenHarnessToolCallback implements ToolCallback {

    private final org.springframework.ai.tool.definition.ToolDefinition toolDefinition;

    OpenHarnessToolCallback(ToolDefinition definition, ObjectMapper objectMapper) {
        Objects.requireNonNull(definition, "definition must not be null");
        ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.toolDefinition = org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(writeJson(mapper, definition.parametersSchema()))
                .build();
    }

    @Override
    public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        throw new UnsupportedOperationException("OpenHarness4j controls tool execution");
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new LLMAdapterException("failed to serialize OpenHarness tool schema", ex);
        }
    }
}
