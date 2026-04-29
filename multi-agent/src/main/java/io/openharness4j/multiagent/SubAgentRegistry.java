package io.openharness4j.multiagent;

import java.util.List;
import java.util.Optional;

public interface SubAgentRegistry {

    void register(SubAgentDefinition agent);

    Optional<SubAgentDefinition> get(String agentId);

    List<SubAgentDefinition> list();
}
