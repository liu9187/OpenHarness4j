package io.openharness4j.plugin;

public record PluginRecord(
        PluginDescriptor descriptor,
        PluginStatus status,
        String errorMessage
) {
    public PluginRecord {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
