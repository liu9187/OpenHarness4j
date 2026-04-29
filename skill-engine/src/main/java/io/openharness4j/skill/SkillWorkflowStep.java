package io.openharness4j.skill;

import java.util.Map;

public record SkillWorkflowStep(
        String name,
        SkillStepType type,
        String tool,
        Map<String, Object> args,
        String prompt,
        Map<String, Object> metadata
) {
    public SkillWorkflowStep {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("step name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("step type must not be null");
        }
        tool = tool == null ? "" : tool;
        if (type == SkillStepType.TOOL && tool.isBlank()) {
            throw new IllegalArgumentException("tool step must declare tool");
        }
        args = args == null ? Map.of() : Map.copyOf(args);
        prompt = prompt == null ? "" : prompt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SkillWorkflowStep tool(String name, String tool, Map<String, Object> args) {
        return new SkillWorkflowStep(name, SkillStepType.TOOL, tool, args, "", Map.of());
    }

    public static SkillWorkflowStep llm(String name, String prompt) {
        return new SkillWorkflowStep(name, SkillStepType.LLM, "", Map.of(), prompt, Map.of());
    }
}
