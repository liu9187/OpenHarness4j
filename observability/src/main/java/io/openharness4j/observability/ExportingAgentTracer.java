package io.openharness4j.observability;

import io.openharness4j.api.FinishReason;

public class ExportingAgentTracer extends DefaultAgentTracer {

    private final ObservationExporter exporter;

    public ExportingAgentTracer(ObservationExporter exporter) {
        this.exporter = exporter == null ? observation -> { } : exporter;
    }

    @Override
    public void finish(AgentTrace trace, FinishReason finishReason) {
        if (trace != null) {
            exporter.export(AgentObservation.from(trace, finishReason));
        }
    }
}
