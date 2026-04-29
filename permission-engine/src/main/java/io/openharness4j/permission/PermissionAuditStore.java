package io.openharness4j.permission;

import java.util.List;

public interface PermissionAuditStore {

    void append(PermissionAuditEvent event);

    List<PermissionAuditEvent> list();

    List<PermissionAuditEvent> byTraceId(String traceId);
}
