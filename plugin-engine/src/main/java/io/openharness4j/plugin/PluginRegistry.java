package io.openharness4j.plugin;

import java.util.List;
import java.util.Optional;

public interface PluginRegistry {

    void register(PluginDescriptor descriptor);

    void markActive(String pluginId);

    void markFailed(String pluginId, String errorMessage);

    Optional<PluginRecord> get(String pluginId);

    List<PluginRecord> list();
}
