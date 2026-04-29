package io.openharness4j.cli;

import java.util.List;

public record CliComponentStatus(String name, String status, List<String> details) {
    public CliComponentStatus {
        name = name == null ? "" : name;
        status = status == null ? "UNKNOWN" : status;
        details = details == null ? List.of() : List.copyOf(details);
    }
}
