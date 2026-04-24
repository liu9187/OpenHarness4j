package io.openharness4j.memory;

import io.openharness4j.api.Message;
import io.openharness4j.api.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryWindowPolicyTest {

    @Test
    void trimsToTailWhenSummaryIsDisabled() {
        MemoryWindowPolicy policy = MemoryWindowPolicy.tailOnly(2);

        List<Message> compacted = policy.apply(List.of(
                Message.user("one"),
                Message.assistant("two"),
                Message.user("three")
        ));

        assertEquals(List.of(Message.assistant("two"), Message.user("three")), compacted);
    }

    @Test
    void summarizesOverflowAndKeepsRecentMessages() {
        MemoryWindowPolicy policy = new MemoryWindowPolicy(3, true, new SimpleMemorySummarizer());

        List<Message> compacted = policy.apply(List.of(
                Message.user("one"),
                Message.assistant("two"),
                Message.user("three"),
                Message.assistant("four")
        ));

        assertEquals(3, compacted.size());
        assertEquals(MessageRole.SYSTEM, compacted.get(0).role());
        assertTrue(compacted.get(0).content().contains("one"));
        assertTrue(compacted.get(0).content().contains("two"));
        assertEquals(Message.user("three"), compacted.get(1));
        assertEquals(Message.assistant("four"), compacted.get(2));
    }
}
