package io.openharness4j.permission;

import java.util.ArrayList;
import java.util.List;

public class InMemoryPermissionAuditStore implements PermissionAuditStore {

    private final List<PermissionAuditEvent> events = new ArrayList<>();

    @Override
    public synchronized void append(PermissionAuditEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    @Override
    public synchronized List<PermissionAuditEvent> list() {
        return List.copyOf(events);
    }

    @Override
    public synchronized List<PermissionAuditEvent> byTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return events.stream()
                .filter(event -> traceId.equals(event.traceId()))
                .toList();
    }
}
