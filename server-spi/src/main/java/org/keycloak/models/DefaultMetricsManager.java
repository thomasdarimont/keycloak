package org.keycloak.models;

import org.keycloak.models.metrics.KeycloakMetric;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class DefaultMetricsManager implements MetricsRecorder {

    protected final KeycloakSessionFactory keycloakSessionFactory;

    protected final ConcurrentMap<String, KeycloakMetric> keycloakMetricMap;

    public DefaultMetricsManager(KeycloakSessionFactory keycloakSessionFactory) {
        this.keycloakSessionFactory = keycloakSessionFactory;
        this.keycloakMetricMap = new ConcurrentHashMap<>();
    }

    @Override
    public void recordMetric(String name, Supplier<Number> valueSupplier, String... tags) {
        // NOOP
    }

    @Override
    public KeycloakMetric getMetric(String metricName) {
        return keycloakMetricMap.get(metricName);
    }
}
