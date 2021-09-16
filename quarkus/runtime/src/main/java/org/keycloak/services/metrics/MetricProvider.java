package org.keycloak.services.metrics;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

public interface MetricProvider extends Provider {

    /**
     * Allows clients to register custom metrics
     * @param metricRegistry
     * @param metricAccessor gives access to current metric values
     */
    void registerMetrics(MetricRegistry metricRegistry, MetricAccessor metricAccessor);

    /**
     * Callback for realm specific metric updates.
     *
     * @param session
     * @param realm
     * @param metricUpdater function to update a given metric
     */
    void updateRealmMetrics(KeycloakSession session, RealmModel realm, MetricUpdater metricUpdater);

    default void close() {
    }
}

