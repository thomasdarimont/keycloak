package org.keycloak.models.metrics;

@FunctionalInterface
public interface MetricComputation {

    Number compute(KeycloakMetricsContext context);
}
