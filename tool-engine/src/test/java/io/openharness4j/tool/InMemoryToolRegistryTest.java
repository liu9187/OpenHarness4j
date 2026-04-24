package io.openharness4j.tool;

import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryToolRegistryTest {

    @Test
    void registersAndFindsTool() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new TestTool("echo"));

        assertTrue(registry.get("echo").isPresent());
        assertEquals(1, registry.definitions().size());
        assertEquals("echo", registry.definitions().get(0).name());
    }

    @Test
    void rejectsDuplicateToolNames() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new TestTool("echo"));

        assertThrows(IllegalArgumentException.class, () -> registry.register(new TestTool("echo")));
    }

    private record TestTool(String name) implements Tool {

        @Override
        public String description() {
            return "test tool";
        }

        @Override
        public ToolResult execute(ToolContext context) {
            return ToolResult.success("ok");
        }
    }
}
