package io.openharness4j.personal.team;

import java.util.Optional;

public interface TeamRuntime {

    TeamAgentSubmission spawn(TeamAgentRequest request);

    Optional<TeamAgentSnapshot> get(String taskId);

    boolean cancel(String taskId);

    Optional<TeamAgentArchive> archive(String taskId);
}
