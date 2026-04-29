package io.openharness4j.plugin;

import io.openharness4j.multiagent.InMemorySubAgentRegistry;
import io.openharness4j.skill.InMemorySkillRegistry;
import io.openharness4j.task.InMemoryTaskRegistry;
import io.openharness4j.tool.InMemoryToolRegistry;
import io.openharness4j.tool.Tool;
import io.openharness4j.api.ToolContext;
import io.openharness4j.api.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManagerTest {

    @Test
    void activatesPluginAndRegistersContribution() {
        InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
        InMemoryPluginRegistry pluginRegistry = new InMemoryPluginRegistry();
        PluginContext context = new PluginContext(
                toolRegistry,
                new InMemorySkillRegistry(),
                new InMemoryTaskRegistry(),
                new InMemorySubAgentRegistry()
        );
        OpenHarnessPlugin plugin = new EchoPlugin();

        new PluginManager(pluginRegistry, context, List.of(plugin)).activateAll();

        assertEquals(PluginStatus.ACTIVE, pluginRegistry.get("echo-plugin").orElseThrow().status());
        assertTrue(toolRegistry.get("plugin_echo").isPresent());
    }

    private static final class EchoPlugin implements OpenHarnessPlugin {
        @Override
        public PluginDescriptor descriptor() {
            return new PluginDescriptor("echo-plugin", "1.0.0", "Echo Plugin");
        }

        @Override
        public void activate(PluginContext context) {
            context.toolRegistry().register(new Tool() {
                @Override
                public String name() {
                    return "plugin_echo";
                }

                @Override
                public String description() {
                    return "Plugin echo";
                }

                @Override
                public ToolResult execute(ToolContext context) {
                    return ToolResult.success("ok");
                }
            });
        }
    }
}
