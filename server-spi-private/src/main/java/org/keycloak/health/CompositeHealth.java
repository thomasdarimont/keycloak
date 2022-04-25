package org.keycloak.health;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompositeHealth implements Health {

    private final String name;
    private final List<Health> checks;

    public CompositeHealth(String name) {
        this.name = name;
        this.checks = new ArrayList<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public HealthState getState() {
        return checks.stream() //
                .map(Health::getState) //
                .filter(HealthState.DOWN::equals) //
                .findAny() //
                .orElse(HealthState.UP);
    }

    @Override
    public Map<String, Object> getData() {

        Map<String, Object> details = new LinkedHashMap<>(checks.size());

        for (Health healthInfo : checks) {
            details.put(healthInfo.getName(), healthInfo.getData());
        }

        return details;
    }

    public List<Health> getChecks() {
        return checks;
    }

    public void add(Health health) {
        checks.add(health);
    }

    public void addAll(CompositeHealth compositeHealthStatus) {
        checks.addAll(compositeHealthStatus.checks);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return checks.isEmpty();
    }
}