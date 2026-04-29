package io.openharness4j.personal.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryTeamAgentRegistry implements TeamAgentRegistry {

    private final ConcurrentMap<String, TeamAgentDefinition> agents = new ConcurrentHashMap<>();

    @Override
    public void register(TeamAgentDefinition agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent must not be null");
        }
        TeamAgentDefinition previous = agents.putIfAbsent(agent.agentId(), agent);
        if (previous != null) {
            throw new IllegalArgumentException("team agent already registered: " + agent.agentId());
        }
    }

    @Override
    public Optional<TeamAgentDefinition> get(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentId));
    }

    @Override
    public List<TeamAgentDefinition> list() {
        return new ArrayList<>(agents.values());
    }

    @Override
    public boolean remove(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        return agents.remove(agentId) != null;
    }
}
