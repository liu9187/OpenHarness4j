package io.openharness4j.multiagent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultPlanningAgent implements PlanningAgent {

    public static final String TASKS_METADATA_KEY = "multiAgentTasks";

    @Override
    public List<AgentTask> plan(MultiAgentRequest request, List<SubAgentDefinition> agents) {
        Object configuredTasks = request.metadata().get(TASKS_METADATA_KEY);
        if (configuredTasks instanceof Iterable<?> iterable) {
            return parseTasks(iterable, request);
        }
        return agents.stream()
                .map(agent -> new AgentTask(
                        null,
                        agent.agentId(),
                        instructionFor(agent, request),
                        Map.of("role", agent.role())
                ))
                .toList();
    }

    private static List<AgentTask> parseTasks(Iterable<?> tasks, MultiAgentRequest request) {
        List<AgentTask> parsed = new ArrayList<>();
        for (Object item : tasks) {
            if (item instanceof AgentTask task) {
                parsed.add(task);
                continue;
            }
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("multiAgentTasks item must be AgentTask or map");
            }
            Map<String, Object> values = stringMap(map);
            parsed.add(new AgentTask(
                    text(values, "taskId", null),
                    text(values, "agentId", "agentId"),
                    text(values, "instruction", request.input()),
                    objectMap(values.get("metadata"))
            ));
        }
        return parsed;
    }

    private static String instructionFor(SubAgentDefinition agent, MultiAgentRequest request) {
        if (agent.role().isBlank()) {
            return request.input();
        }
        return "Role: " + agent.role() + "\nTask: " + request.input();
    }

    private static String text(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        if (value == null) {
            if (fallback == null) {
                return null;
            }
            return fallback;
        }
        return String.valueOf(value);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("metadata must be an object");
        }
        return stringMap(map);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return values;
    }
}
