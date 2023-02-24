package org.keycloak.metrics;

import org.keycloak.models.metrics.KeycloakMetric;
import org.keycloak.provider.Provider;

import java.util.List;

public interface KeycloakMetricsProvider extends Provider {

    List<KeycloakMetric> createMetrics();

    default void close() {
        // NOOP
    }
}
