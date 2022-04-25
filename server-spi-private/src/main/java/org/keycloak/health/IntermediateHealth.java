package org.keycloak.health;

import java.util.LinkedHashMap;
import java.util.Map;

public class IntermediateHealth implements Health {

    private String checkName;

    private HealthState state;

    private final Map<String, Object> data;

    public IntermediateHealth(String name) {
        this.state = HealthState.UP;
        this.checkName = name;
        this.data = new LinkedHashMap<>();
    }

    public IntermediateHealth withState(HealthState state) {
        this.state = state;
        return this;
    }

    public IntermediateHealth withName(String name) {
        this.checkName = name;
        return this;
    }

    public IntermediateHealth withData(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public IntermediateHealth withData(Map<String, Object> values) {
        data.putAll(values);
        return this;
    }

    public String getName() {
        return checkName;
    }

    public HealthState getState() {
        return state;
    }

    public Map<String, Object> getData() {
        return data;
    }

}
