package io.openharness4j.plugin;

import java.util.List;

public class PluginManager {

    private final PluginRegistry registry;
    private final PluginContext context;
    private final List<OpenHarnessPlugin> plugins;

    public PluginManager(PluginRegistry registry, PluginContext context, List<OpenHarnessPlugin> plugins) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.registry = registry;
        this.context = context;
        this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    public void activateAll() {
        for (OpenHarnessPlugin plugin : plugins) {
            PluginDescriptor descriptor = plugin.descriptor();
            registry.register(descriptor);
            try {
                plugin.activate(context);
                registry.markActive(descriptor.id());
            } catch (RuntimeException ex) {
                registry.markFailed(descriptor.id(), safeMessage(ex));
            }
        }
    }

    public List<OpenHarnessPlugin> plugins() {
        return plugins;
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
