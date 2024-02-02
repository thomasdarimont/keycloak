package org.keycloak.config;

import java.util.Set;

public class CompositeConfig implements ConfigWrapper {

    private final Set<ConfigWrapper> configs;

    public CompositeConfig(Set<ConfigWrapper> configs) {
        this.configs = configs;
    }

    @Override
    public String getType() {
        return "composite";
    }

    @Override
    public String getSource() {
        return "null";
    }

    @Override
    public boolean containsKey(String key) {
        return configs.stream().anyMatch(config -> config.containsKey(key));
    }

    @Override
    public <T> T getValue(String key) {
        for (ConfigWrapper config : configs) {
            if (config.containsKey(key)) {
                return config.getValue(key);
            }
        }
        return null;
    }
}
