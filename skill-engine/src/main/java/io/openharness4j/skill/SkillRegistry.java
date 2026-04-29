package io.openharness4j.skill;

import java.util.List;
import java.util.Optional;

public interface SkillRegistry {

    void register(SkillDefinition skill);

    Optional<SkillDefinition> get(String id);

    Optional<SkillDefinition> get(String id, String version);

    List<SkillDefinition> list();
}
