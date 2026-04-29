package io.openharness4j.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySkillRegistry implements SkillRegistry {

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();
    private final Map<String, String> latestKeysById = new LinkedHashMap<>();

    @Override
    public synchronized void register(SkillDefinition skill) {
        if (skill == null) {
            throw new IllegalArgumentException("skill must not be null");
        }
        String key = key(skill.id(), skill.version());
        if (skills.containsKey(key)) {
            throw new IllegalArgumentException("skill already registered: " + key);
        }
        skills.put(key, skill);
        latestKeysById.put(skill.id(), key);
    }

    @Override
    public synchronized Optional<SkillDefinition> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestKeysById.get(id)).map(skills::get);
    }

    @Override
    public synchronized Optional<SkillDefinition> get(String id, String version) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        if (version == null || version.isBlank()) {
            return get(id);
        }
        return Optional.ofNullable(skills.get(key(id, version)));
    }

    @Override
    public synchronized List<SkillDefinition> list() {
        return new ArrayList<>(skills.values());
    }

    private static String key(String id, String version) {
        return id + ":" + version;
    }
}
