package io.openharness4j.skill;

public record SkillPrompt(
        String system,
        String user
) {
    public SkillPrompt {
        system = system == null ? "" : system;
        user = user == null ? "" : user;
    }

    public static SkillPrompt empty() {
        return new SkillPrompt("", "");
    }
}
