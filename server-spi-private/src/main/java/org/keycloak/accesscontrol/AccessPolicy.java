package org.keycloak.accesscontrol;

import java.util.Map;

public class AccessPolicy {

    private String id;

    private String name;

    private String description;

    private Map<String, Object> config;

    public AccessPolicy() {
    }

    public AccessPolicy(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "AccessPolicy{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
    }
}
