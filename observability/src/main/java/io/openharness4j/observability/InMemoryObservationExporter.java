package io.openharness4j.observability;

import java.util.ArrayList;
import java.util.List;

public class InMemoryObservationExporter implements ObservationExporter {

    private final List<AgentObservation> observations = new ArrayList<>();

    @Override
    public synchronized void export(AgentObservation observation) {
        if (observation != null) {
            observations.add(observation);
        }
    }

    public synchronized List<AgentObservation> list() {
        return List.copyOf(observations);
    }
}
