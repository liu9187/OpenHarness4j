package io.openharness4j.multiagent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeyValueConflictResolver implements ConflictResolver {

    @Override
    public List<AgentConflict> detect(List<AgentTaskResult> results) {
        Map<String, SeenValue> seen = new LinkedHashMap<>();
        List<AgentConflict> conflicts = new ArrayList<>();
        for (AgentTaskResult result : results) {
            if (result.status() != AgentTaskStatus.SUCCEEDED) {
                continue;
            }
            for (Map.Entry<String, String> entry : keyValues(result.content()).entrySet()) {
                SeenValue existing = seen.get(entry.getKey());
                if (existing == null) {
                    seen.put(entry.getKey(), new SeenValue(result.agentId(), entry.getValue()));
                    continue;
                }
                if (!existing.value().equals(entry.getValue())) {
                    conflicts.add(new AgentConflict(
                            entry.getKey(),
                            existing.agentId(),
                            result.agentId(),
                            existing.value(),
                            entry.getValue(),
                            "agents returned different values for " + entry.getKey()
                    ));
                }
            }
        }
        return conflicts;
    }

    private static Map<String, String> keyValues(String content) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : content.split("\\R")) {
            int index = line.indexOf('=');
            if (index <= 0 || index == line.length() - 1) {
                continue;
            }
            String key = line.substring(0, index).trim();
            String value = line.substring(index + 1).trim();
            if (!key.isBlank() && !value.isBlank()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private record SeenValue(String agentId, String value) {
    }
}
