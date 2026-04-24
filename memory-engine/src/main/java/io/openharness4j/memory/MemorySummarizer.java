package io.openharness4j.memory;

import io.openharness4j.api.Message;

import java.util.List;

public interface MemorySummarizer {

    Message summarize(List<Message> messages);
}
