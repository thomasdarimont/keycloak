package org.keycloak.services.metrics;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.keycloak.common.Version;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import static org.keycloak.services.metrics.Metrics.tag;

public class DefaultMetricProvider implements MetricProvider {

    @Override
    public void updateRealmMetrics(KeycloakSession session, RealmModel realm, MetricUpdater metricUpdater) {

        // Performs the dynamic metrics collection: this is called when metrics need to be refreshed

        metricUpdater.updateMetricValue(Metrics.USERS_TOTAL, realm, session.users().getUsersCount(realm));
        metricUpdater.updateMetricValue(Metrics.CLIENTS_TOTAL, realm, session.clients().getClientsCount(realm));
        metricUpdater.updateMetricValue(Metrics.GROUPS_TOTAL, realm, session.groups().getGroupsCount(realm, false));
    }

    @Override
    public void registerMetrics(MetricRegistry metricRegistry, MetricAccessor metricAccessor) {

        // we should only register metrics here and avoid expensive initializations!

        metricRegistry.register(Metrics.SERVER_VERSION, (Gauge<Double>) () -> 0.0, tag("version", Version.VERSION));
        metricRegistry.register(Metrics.METRICS_REFRESH, (Gauge<Double>) () -> metricAccessor.getMetricValue(Metrics.METRICS_REFRESH));
    }
}