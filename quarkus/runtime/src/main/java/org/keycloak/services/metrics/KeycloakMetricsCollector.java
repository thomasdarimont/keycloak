package org.keycloak.services.metrics;

import com.google.common.base.Stopwatch;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.keycloak.services.metrics.KeycloakMetrics.TAG_REALM;
import static org.keycloak.services.metrics.KeycloakMetrics.tag;

public class KeycloakMetricsCollector {

    private static final int CUSTOM_METRICS_REFRESH_INTERVAL_MILLIS = Integer.getInteger("KEYCLOAK_METRICS_REFRESH_INTERVAL_MILLIS", 5000);
    private final KeycloakSessionFactory sessionFactory;
    private final MetricRegistry metricsRegistry;

    private volatile long lastUpdateTimestamp;

    private Map<String, Double> customMetricsStore;

    public KeycloakMetricsCollector(KeycloakSessionFactory sessionFactory, MetricRegistry metricsRegistry) {
        this.sessionFactory = sessionFactory;
        this.metricsRegistry = metricsRegistry;
    }

    public Double getCustomMetric(Metadata metric) {
        return getCustomMetric(metric.getName());
    }

    public Double getCustomMetric(String metricName) {

        if (isRefreshNecessary()) {
            refreshMetrics();
        }

        Map<String, Double> customMetrics = this.customMetricsStore;
        if (customMetrics == null) {
            return -1.0;
        }

        Double count = customMetrics.get(metricName);
        if (count != null) {
            return count;
        }

        return -1.0;
    }

    private boolean isRefreshNecessary() {

        if (customMetricsStore == null) {
            return true;
        }

        long millisSinceLastUpdate = System.currentTimeMillis() - lastUpdateTimestamp;
        return millisSinceLastUpdate > CUSTOM_METRICS_REFRESH_INTERVAL_MILLIS;
    }

    private void refreshMetrics() {

        if (!isRefreshNecessary()) {
            return;
        }

        synchronized (this) {

            if (!isRefreshNecessary()) {
                return;
            }

            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<String, Double> metricsBuffer = new HashMap<>();

            // extract current metrics here to avoid excessive object creation
            Map<MetricID, Metric> currentMetrics = metricsRegistry.getMetrics();

            KeycloakModelUtils.runJobInTransaction(sessionFactory, session -> {
                RealmMetrics realmMetrics = new RealmMetrics(session, metricsRegistry, currentMetrics, metricsBuffer, this::getCustomMetric);
                collectRealmMetricsIntoBuffer(session, realmMetrics);
            });

            this.customMetricsStore = metricsBuffer;
            this.lastUpdateTimestamp = System.currentTimeMillis();
            double lastUpdateDurationMillis = stopwatch.elapsed().toMillis();
            customMetricsStore.put(KeycloakMetrics.METRICS_REFRESH.getName(), lastUpdateDurationMillis);
        }
    }

    private void collectRealmMetricsIntoBuffer(KeycloakSession session, RealmMetrics realmMetrics) {
        session.realms().getRealmsStream().forEach(realmMetrics::collect);
    }


    static class RealmMetrics {

        private final KeycloakSession session;
        private final MetricRegistry metricsRegistry;
        private final Map<MetricID, Metric> currentMetrics;
        private final Map<String, Double> metricsBuffer;
        private final Function<String, Double> metricsLookup;

        public RealmMetrics(
                KeycloakSession session,
                MetricRegistry metricsRegistry,
                Map<MetricID, Metric> currentMetrics,
                Map<String, Double> metricsBuffer,
                Function<String, Double> metricsLookup) {
            this.session = session;
            this.metricsRegistry = metricsRegistry;
            this.currentMetrics = currentMetrics;
            this.metricsBuffer = metricsBuffer;
            this.metricsLookup = metricsLookup;
        }

        private double countClientsInRealm(RealmModel realm) {
            return session.clients().getClientsCount(realm);
        }

        private double countUsersInRealm(RealmModel realm) {
            return session.users().getUsersCount(realm);
        }

        public void collect(RealmModel realm) {

            MetricID metricID;
            metricID = ensureRealmMetric(KeycloakMetrics.USERS_TOTAL, realm.getName());
            metricsBuffer.put(metricID.toString(), countUsersInRealm(realm));

            metricID = ensureRealmMetric(KeycloakMetrics.CLIENTS_TOTAL, realm.getName());
            metricsBuffer.put(metricID.toString(), countClientsInRealm(realm));
        }

        private MetricID ensureRealmMetric(Metadata metric, String realmName) {

            Tag[] tags = {tag(TAG_REALM, realmName)};
            MetricID key = new MetricID(metric.getName(), tags);
            boolean metricPresent = currentMetrics.containsKey(key);
            if (metricPresent) {
                // avoid duplicate metric registration
                return key;
            }

            Gauge<Double> metricUpdater = () -> metricsLookup.apply(key.toString());
            metricsRegistry.register(metric, metricUpdater, tags);
            return key;
        }

    }

}
