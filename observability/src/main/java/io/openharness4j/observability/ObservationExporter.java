package io.openharness4j.observability;

public interface ObservationExporter {

    void export(AgentObservation observation);
}
