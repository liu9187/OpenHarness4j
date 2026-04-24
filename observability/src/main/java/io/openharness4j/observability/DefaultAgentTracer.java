package io.openharness4j.observability;

import io.openharness4j.api.AgentRequest;

import java.util.UUID;

public class DefaultAgentTracer implements AgentTracer {

    public static final String TRACE_ID_METADATA_KEY = "traceId";

    @Override
    public AgentTrace start(AgentRequest request) {
        Object providedTraceId = request.metadata().get(TRACE_ID_METADATA_KEY);
        if (providedTraceId instanceof String traceId && !traceId.isBlank()) {
            return new AgentTrace(traceId);
        }
        return new AgentTrace(UUID.randomUUID().toString());
    }
}
