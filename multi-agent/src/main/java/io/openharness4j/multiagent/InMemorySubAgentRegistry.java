package io.openharness4j.multiagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySubAgentRegistry implements SubAgentRegistry {

    private final Map<String, SubAgentDefinition> agents = new ConcurrentHashMap<>();

    @Override
    public void register(SubAgentDefinition agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent must not be null");
        }
        SubAgentDefinition previous = agents.putIfAbsent(agent.agentId(), agent);
        if (previous != null) {
            throw new IllegalArgumentException("sub agent already registered: " + agent.agentId());
        }
    }

    @Override
    public Optional<SubAgentDefinition> get(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentId));
    }

    @Override
    public List<SubAgentDefinition> list() {
        return new ArrayList<>(agents.values());
    }
}
