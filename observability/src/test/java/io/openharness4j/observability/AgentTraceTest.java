package io.openharness4j.observability;

import io.openharness4j.api.FinishReason;
import io.openharness4j.api.ToolCall;
import io.openharness4j.api.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTraceTest {

    @Test
    void redactsSensitiveToolArgumentsInTraceRecords() {
        AgentTrace trace = new AgentTrace("trace-1");

        trace.recordToolResult(
                new ToolCall("call-1", "http", Map.of("token", "secret-token", "query", "hello")),
                ToolResult.success("ok"),
                1
        );

        assertEquals("***", trace.toolCalls().get(0).args().get("token"));
        assertEquals("hello", trace.toolCalls().get(0).args().get("query"));
    }

    @Test
    void exportingTracerPublishesFinishedObservation() {
        InMemoryObservationExporter exporter = new InMemoryObservationExporter();
        ExportingAgentTracer tracer = new ExportingAgentTracer(exporter);
        AgentTrace trace = new AgentTrace("trace-1");

        tracer.finish(trace, FinishReason.STOP);

        assertEquals(1, exporter.list().size());
        assertEquals(FinishReason.STOP, exporter.list().get(0).finishReason());
    }
}
