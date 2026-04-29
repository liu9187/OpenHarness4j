package io.openharness4j.cli;

import io.openharness4j.skill.InMemorySkillRegistry;
import io.openharness4j.skill.MarkdownSkillLoader;
import io.openharness4j.skill.SkillDefinition;
import io.openharness4j.skill.YamlSkillLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class CliSkillLoader {

    private CliSkillLoader() {
    }

    static InMemorySkillRegistry load(CliOptions options) {
        InMemorySkillRegistry registry = new InMemorySkillRegistry();
        MarkdownSkillLoader markdownLoader = new MarkdownSkillLoader();
        YamlSkillLoader yamlLoader = new YamlSkillLoader();
        for (Path location : options.skillLocations) {
            loadLocation(registry, markdownLoader, yamlLoader, location);
        }
        return registry;
    }

    private static void loadLocation(
            InMemorySkillRegistry registry,
            MarkdownSkillLoader markdownLoader,
            YamlSkillLoader yamlLoader,
            Path location
    ) {
        Path normalized = location.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("skill location does not exist: " + normalized);
        }
        try {
            if (Files.isDirectory(normalized)) {
                try (var stream = Files.walk(normalized)) {
                    stream.filter(Files::isRegularFile)
                            .filter(CliSkillLoader::isSkillFile)
                            .sorted()
                            .forEach(path -> register(registry, markdownLoader, yamlLoader, path));
                }
            } else {
                register(registry, markdownLoader, yamlLoader, normalized);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to load skill location: " + normalized, ex);
        }
    }

    private static void register(
            InMemorySkillRegistry registry,
            MarkdownSkillLoader markdownLoader,
            YamlSkillLoader yamlLoader,
            Path path
    ) {
        try {
            SkillDefinition skill = extension(path).equals("md")
                    ? markdownLoader.load(path)
                    : yamlLoader.load(path);
            registry.register(skill);
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to load skill: " + path, ex);
        }
    }

    private static boolean isSkillFile(Path path) {
        String extension = extension(path);
        return extension.equals("md") || extension.equals("yaml") || extension.equals("yml");
    }

    private static String extension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
