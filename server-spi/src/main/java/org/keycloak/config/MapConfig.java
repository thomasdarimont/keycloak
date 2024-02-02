package org.keycloak.config;

import java.util.Collections;
import java.util.Map;

public class MapConfig implements ConfigWrapper {

    private final Map<String, Object> config;

    public MapConfig(Map<String, Object> config) {
        this.config = config == null ? Collections.emptyMap() : config;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    @Override
    public String getType() {
        return "Map";
    }

    @Override
    public String getSource() {
        return "configMap";
    }

    @Override
    public boolean containsKey(String key) {
        return config.containsKey(key);
    }

    @Override
    public <T> T getValue(String key) {
        return (T)config.get(key);
    }
}
