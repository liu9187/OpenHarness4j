package io.openharness4j.plugin;

import io.openharness4j.multiagent.SubAgentRegistry;
import io.openharness4j.skill.SkillRegistry;
import io.openharness4j.task.TaskRegistry;
import io.openharness4j.tool.ToolRegistry;

public record PluginContext(
        ToolRegistry toolRegistry,
        SkillRegistry skillRegistry,
        TaskRegistry taskRegistry,
        SubAgentRegistry subAgentRegistry
) {
    public PluginContext {
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry must not be null");
        }
        if (skillRegistry == null) {
            throw new IllegalArgumentException("skillRegistry must not be null");
        }
        if (taskRegistry == null) {
            throw new IllegalArgumentException("taskRegistry must not be null");
        }
        if (subAgentRegistry == null) {
            throw new IllegalArgumentException("subAgentRegistry must not be null");
        }
    }
}
