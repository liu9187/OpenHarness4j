package io.openharness4j.multiagent;

import java.util.List;

public interface ConflictResolver {

    List<AgentConflict> detect(List<AgentTaskResult> results);
}
