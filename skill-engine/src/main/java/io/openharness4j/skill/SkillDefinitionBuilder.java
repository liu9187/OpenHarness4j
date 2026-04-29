package io.openharness4j.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillDefinitionBuilder {

    private final String id;
    private final String version;
    private String name;
    private String description;
    private Map<String, Object> inputSchema = Map.of();
    private Map<String, Object> outputSchema = Map.of();
    private SkillPrompt prompt = SkillPrompt.empty();
    private final List<String> requiredTools = new ArrayList<>();
    private final List<SkillWorkflowStep> workflow = new ArrayList<>();
    private Map<String, Object> metadata = Map.of();

    SkillDefinitionBuilder(String id, String version) {
        this.id = id;
        this.version = version;
    }

    public SkillDefinitionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SkillDefinitionBuilder description(String description) {
        this.description = description;
        return this;
    }

    public SkillDefinitionBuilder inputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        return this;
    }

    public SkillDefinitionBuilder outputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        return this;
    }

    public SkillDefinitionBuilder prompt(String system, String user) {
        this.prompt = new SkillPrompt(system, user);
        return this;
    }

    public SkillDefinitionBuilder requiredTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        this.requiredTools.add(toolName);
        return this;
    }

    public SkillDefinitionBuilder requiredTools(List<String> toolNames) {
        if (toolNames != null) {
            toolNames.forEach(this::requiredTool);
        }
        return this;
    }

    public SkillDefinitionBuilder toolStep(String name, String tool, Map<String, Object> args) {
        this.workflow.add(SkillWorkflowStep.tool(name, tool, args));
        return this;
    }

    public SkillDefinitionBuilder llmStep(String name, String prompt) {
        this.workflow.add(SkillWorkflowStep.llm(name, prompt));
        return this;
    }

    public SkillDefinitionBuilder metadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        return this;
    }

    public SkillDefinition build() {
        return new SkillDefinition(
                id,
                version,
                name,
                description,
                new LinkedHashMap<>(inputSchema),
                new LinkedHashMap<>(outputSchema),
                prompt,
                List.copyOf(requiredTools),
                List.copyOf(workflow),
                new LinkedHashMap<>(metadata)
        );
    }
}
