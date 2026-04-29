package io.openharness4j.cli;

import java.util.List;

public record CliDryRunReport(
        boolean ready,
        List<CliComponentStatus> components,
        List<String> risks,
        List<String> nextActions
) {
    public CliDryRunReport {
        components = components == null ? List.of() : List.copyOf(components);
        risks = risks == null ? List.of() : List.copyOf(risks);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}
