package io.openharness4j.skill;

public enum SkillStepType {
    LLM,
    TOOL;

    public static SkillStepType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("step type must not be blank");
        }
        return SkillStepType.valueOf(value.trim().toUpperCase());
    }
}
