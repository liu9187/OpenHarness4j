package io.openharness4j.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryPluginRegistry implements PluginRegistry {

    private final Map<String, PluginRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized void register(PluginDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        if (records.containsKey(descriptor.id())) {
            throw new IllegalArgumentException("plugin already registered: " + descriptor.id());
        }
        records.put(descriptor.id(), new PluginRecord(descriptor, PluginStatus.REGISTERED, ""));
    }

    @Override
    public synchronized void markActive(String pluginId) {
        PluginRecord record = require(pluginId);
        records.put(pluginId, new PluginRecord(record.descriptor(), PluginStatus.ACTIVE, ""));
    }

    @Override
    public synchronized void markFailed(String pluginId, String errorMessage) {
        PluginRecord record = require(pluginId);
        records.put(pluginId, new PluginRecord(record.descriptor(), PluginStatus.FAILED, errorMessage));
    }

    @Override
    public synchronized Optional<PluginRecord> get(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(pluginId));
    }

    @Override
    public synchronized List<PluginRecord> list() {
        return new ArrayList<>(records.values());
    }

    private PluginRecord require(String pluginId) {
        PluginRecord record = records.get(pluginId);
        if (record == null) {
            throw new IllegalArgumentException("plugin not registered: " + pluginId);
        }
        return record;
    }
}
