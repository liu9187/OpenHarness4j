package io.openharness4j.personal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryPersonalAgentAuditStore implements PersonalAgentAuditStore {

    private final List<PersonalAgentAuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(PersonalAgentAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        events.add(event);
    }

    @Override
    public List<PersonalAgentAuditEvent> list() {
        return List.copyOf(events);
    }
}
