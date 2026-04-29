package io.openharness4j.plugin;

public interface OpenHarnessPlugin {

    PluginDescriptor descriptor();

    void activate(PluginContext context);

    default void deactivate(PluginContext context) {
    }
}
