package org.keycloak.models;

import org.keycloak.models.metrics.KeycloakMetric;

import java.util.function.Supplier;

public interface MetricsRecorder {

    void recordMetric(String name, Supplier<Number> valueSupplier, String... tags);

    KeycloakMetric getMetric(String metricName);
}
