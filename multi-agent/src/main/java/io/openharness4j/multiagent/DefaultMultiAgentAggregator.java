package io.openharness4j.multiagent;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultMultiAgentAggregator implements MultiAgentAggregator {

    @Override
    public String aggregate(
            MultiAgentRequest request,
            List<AgentTask> tasks,
            List<AgentTaskResult> results,
            List<AgentConflict> conflicts
    ) {
        StringBuilder output = new StringBuilder();
        if (!conflicts.isEmpty()) {
            output.append("Conflicts detected:\n");
            for (AgentConflict conflict : conflicts) {
                output.append("- ")
                        .append(conflict.key())
                        .append(": ")
                        .append(conflict.firstAgentId())
                        .append("=")
                        .append(conflict.firstValue())
                        .append(", ")
                        .append(conflict.secondAgentId())
                        .append("=")
                        .append(conflict.secondValue())
                        .append('\n');
            }
        }
        output.append("Agent results:\n");
        output.append(results.stream()
                .map(result -> "- " + result.agentId() + " [" + result.status() + "]: " + oneLine(result.content(), result.errorMessage()))
                .collect(Collectors.joining("\n")));
        return output.toString();
    }

    private static String oneLine(String content, String fallback) {
        String value = content == null || content.isBlank() ? fallback : content;
        return value == null ? "" : value.replace('\n', ' ').trim();
    }
}
