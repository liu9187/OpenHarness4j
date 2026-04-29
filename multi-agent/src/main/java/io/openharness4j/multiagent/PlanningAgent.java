package io.openharness4j.multiagent;

import java.util.List;

public interface PlanningAgent {

    List<AgentTask> plan(MultiAgentRequest request, List<SubAgentDefinition> agents);
}
