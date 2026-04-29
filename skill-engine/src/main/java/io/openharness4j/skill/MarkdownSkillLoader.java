package io.openharness4j.skill;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarkdownSkillLoader {

    private final Yaml yaml = new Yaml();

    public SkillDefinition load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        return load(Files.readString(path), sourceName(path));
    }

    public SkillDefinition load(InputStream inputStream, String sourceName) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }
        return load(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), sourceName);
    }

    public SkillDefinition load(String markdown, String sourceName) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("markdown must not be blank");
        }
        MarkdownParts parts = split(markdown);
        Map<String, Object> frontMatter = parseFrontMatter(parts.frontMatter());
        String id = optionalText(frontMatter, "id");
        if (id.isBlank()) {
            id = slug(optionalText(frontMatter, "name"));
        }
        if (id.isBlank()) {
            id = slug(sourceName == null || sourceName.isBlank() ? "markdown-skill" : sourceName);
        }
        String version = optionalText(frontMatter, "version");
        if (version.isBlank()) {
            version = "0.1.0";
        }
        String name = optionalText(frontMatter, "name");
        if (name.isBlank()) {
            name = id;
        }
        String description = optionalText(frontMatter, "description");
        SkillPrompt prompt = prompt(frontMatter, parts.body());
        List<SkillWorkflowStep> workflow = workflow(frontMatter, parts.body());
        return new SkillDefinition(
                id,
                version,
                name,
                description,
                objectMap(frontMatter.get("inputSchema")),
                objectMap(frontMatter.get("outputSchema")),
                prompt,
                stringList(frontMatter.get("requiredTools")),
                workflow,
                metadata(frontMatter, sourceName)
        );
    }

    private Map<String, Object> parseFrontMatter(String frontMatter) {
        if (frontMatter.isBlank()) {
            return Map.of();
        }
        Object loaded = yaml.load(frontMatter);
        if (loaded == null) {
            return Map.of();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("skill markdown front matter must be an object");
        }
        return stringMap(map);
    }

    private static SkillPrompt prompt(Map<String, Object> frontMatter, String body) {
        Map<String, Object> prompt = objectMap(frontMatter.get("prompt"));
        String system = optionalText(frontMatter, "system");
        if (system.isBlank()) {
            system = optionalText(prompt, "system");
        }
        String user = optionalText(frontMatter, "user");
        if (user.isBlank()) {
            user = optionalText(prompt, "user");
        }
        if (user.isBlank()) {
            user = body.trim();
        }
        return new SkillPrompt(system, user);
    }

    private static List<SkillWorkflowStep> workflow(Map<String, Object> frontMatter, String body) {
        Object value = frontMatter.get("workflow");
        if (value != null) {
            return workflowFromFrontMatter(value);
        }
        return List.of(new SkillWorkflowStep(
                "respond",
                SkillStepType.LLM,
                "",
                Map.of(),
                body.trim(),
                Map.of("source", "markdown")
        ));
    }

    private static List<SkillWorkflowStep> workflowFromFrontMatter(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("workflow must be a list");
        }
        java.util.ArrayList<SkillWorkflowStep> steps = new java.util.ArrayList<>();
        for (Object item : iterable) {
            Map<String, Object> map = objectMap(item);
            steps.add(new SkillWorkflowStep(
                    text(map, "name"),
                    SkillStepType.from(text(map, "type")),
                    optionalText(map, "tool"),
                    objectMap(map.get("args")),
                    optionalText(map, "prompt"),
                    objectMap(map.get("metadata"))
            ));
        }
        return List.copyOf(steps);
    }

    private static MarkdownParts split(String markdown) {
        String normalized = markdown.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new MarkdownParts("", normalized);
        }
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalArgumentException("markdown front matter is not closed");
        }
        String frontMatter = normalized.substring(4, end).trim();
        int bodyStart = normalized.indexOf('\n', end + 1);
        String body = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1);
        return new MarkdownParts(frontMatter, body);
    }

    private static Map<String, Object> metadata(Map<String, Object> frontMatter, String sourceName) {
        Map<String, Object> metadata = new LinkedHashMap<>(objectMap(frontMatter.get("metadata")));
        metadata.put("format", "markdown");
        if (sourceName != null && !sourceName.isBlank()) {
            metadata.put("source", sourceName);
        }
        return Map.copyOf(metadata);
    }

    private static String sourceName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static String slug(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String slug = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\.[^.]+$", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isBlank() ? "" : slug;
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
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (Object item : iterable) {
            values.add(String.valueOf(item));
        }
        return List.copyOf(values);
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

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> result = new java.util.ArrayList<>();
            for (Object item : iterable) {
                result.add(normalize(item));
            }
            return List.copyOf(result);
        }
        return value;
    }

    private record MarkdownParts(String frontMatter, String body) {
    }
}
