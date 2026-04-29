package io.openharness4j.personal.team;

import java.util.List;
import java.util.Optional;

public interface TeamAgentRegistry {

    void register(TeamAgentDefinition agent);

    Optional<TeamAgentDefinition> get(String agentId);

    List<TeamAgentDefinition> list();

    boolean remove(String agentId);
}
