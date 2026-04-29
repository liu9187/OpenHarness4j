package io.openharness4j.skill;

import java.util.Map;

public record SkillRunRequest(
        String skillId,
        String version,
        String sessionId,
        String userId,
        Map<String, Object> input,
        Map<String, Object> metadata
) {
    public SkillRunRequest {
        skillId = requireText(skillId, "skillId");
        version = version == null ? "" : version;
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        input = input == null ? Map.of() : Map.copyOf(input);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static SkillRunRequest of(String skillId, String sessionId, String userId, Map<String, Object> input) {
        return new SkillRunRequest(skillId, "", sessionId, userId, input, Map.of());
    }

    public static SkillRunRequest of(
            String skillId,
            String version,
            String sessionId,
            String userId,
            Map<String, Object> input
    ) {
        return new SkillRunRequest(skillId, version, sessionId, userId, input, Map.of());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
