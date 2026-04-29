package io.openharness4j.multiagent;

import java.util.List;

public interface MultiAgentAggregator {

    String aggregate(
            MultiAgentRequest request,
            List<AgentTask> tasks,
            List<AgentTaskResult> results,
            List<AgentConflict> conflicts
    );
}
