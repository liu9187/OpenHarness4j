package io.openharness4j.plugin;

public record PluginDescriptor(
        String id,
        String version,
        String name
) {
    public PluginDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        name = name == null || name.isBlank() ? id : name;
    }
}
