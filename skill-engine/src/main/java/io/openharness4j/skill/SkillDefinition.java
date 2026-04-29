package io.openharness4j.skill;

import java.util.List;
import java.util.Map;

public record SkillDefinition(
        String id,
        String version,
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        SkillPrompt prompt,
        List<String> requiredTools,
        List<SkillWorkflowStep> workflow,
        Map<String, Object> metadata
) {
    public SkillDefinition {
        id = requireText(id, "id");
        version = requireText(version, "version");
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        prompt = prompt == null ? SkillPrompt.empty() : prompt;
        requiredTools = requiredTools == null ? List.of() : List.copyOf(requiredTools);
        workflow = workflow == null ? List.of() : List.copyOf(workflow);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (workflow.isEmpty()) {
            throw new IllegalArgumentException("workflow must not be empty");
        }
    }

    public static SkillDefinitionBuilder builder(String id, String version) {
        return new SkillDefinitionBuilder(id, version);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
