package io.openharness4j.llm;

import io.openharness4j.api.LLMResponse;
import io.openharness4j.api.Message;
import io.openharness4j.api.ToolDefinition;

import java.util.List;

public interface LLMAdapter {

    LLMResponse chat(List<Message> messages, List<ToolDefinition> tools);
}
