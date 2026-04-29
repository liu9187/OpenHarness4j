package io.openharness4j.skill;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlSkillLoader {

    private final Yaml yaml = new Yaml();

    public SkillDefinition load(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return load(inputStream);
        }
    }

    public SkillDefinition load(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }
        Object loaded = yaml.load(inputStream);
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("skill yaml root must be an object");
        }
        return toSkillDefinition(stringMap(map));
    }

    private SkillDefinition toSkillDefinition(Map<String, Object> map) {
        return new SkillDefinition(
                text(map, "id"),
                text(map, "version"),
                optionalText(map, "name"),
                optionalText(map, "description"),
                objectMap(map.get("inputSchema")),
                objectMap(map.get("outputSchema")),
                prompt(map.get("prompt")),
                stringList(map.get("requiredTools")),
                workflow(map.get("workflow")),
                objectMap(map.get("metadata"))
        );
    }

    private static SkillPrompt prompt(Object value) {
        Map<String, Object> map = objectMap(value);
        return new SkillPrompt(optionalText(map, "system"), optionalText(map, "user"));
    }

    private static List<SkillWorkflowStep> workflow(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("workflow must be a list");
        }
        List<SkillWorkflowStep> steps = new ArrayList<>();
        for (Object item : iterable) {
            Map<String, Object> map = objectMap(item);
            SkillStepType type = SkillStepType.from(text(map, "type"));
            steps.add(new SkillWorkflowStep(
                    text(map, "name"),
                    type,
                    optionalText(map, "tool"),
                    objectMap(map.get("args")),
                    optionalText(map, "prompt"),
                    objectMap(map.get("metadata"))
            ));
        }
        return steps;
    }

    private static String text(Map<String, Object> map, String key) {
        String value = optionalText(map, key);
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static String optionalText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("requiredTools must be a list");
        }
        List<String> values = new ArrayList<>();
        for (Object item : iterable) {
            values.add(String.valueOf(item));
        }
        return values;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected object but got " + value.getClass().getSimpleName());
        }
        return stringMap(map);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                result.add(normalize(item));
            }
            return result;
        }
        return value;
    }
}
