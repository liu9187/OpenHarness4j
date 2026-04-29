package io.openharness4j.runtime;

public enum AgentEventType {
    STARTED,
    LLM_ATTEMPT,
    LLM_RETRY,
    LLM_RESPONSE,
    TEXT_DELTA,
    TOOL_STARTED,
    TOOL_RETRY,
    TOOL_DONE,
    COST_UPDATED,
    DONE,
    ERROR
}
