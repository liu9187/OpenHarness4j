package io.openharness4j.api;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersSchema
) {
    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        description = description == null ? "" : description;
        parametersSchema = parametersSchema == null ? Map.of() : Map.copyOf(parametersSchema);
    }
}
