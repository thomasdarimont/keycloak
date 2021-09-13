package org.keycloak.services.metrics;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.keycloak.common.Version;

public class KeycloakMetrics {

    public static final Metadata SERVER_VERSION = Metadata.builder()
            .withName("keycloak_server_version")
            .withDescription("Keycloak Server Version")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata USERS_TOTAL = Metadata.builder()
            .withName("keycloak_users_total")
            .withDescription("Total users")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata CLIENTS_TOTAL = Metadata.builder()
            .withName("keycloak_clients_total")
            .withDescription("Total clients")
            .withType(MetricType.GAUGE)
            .build();

    public static final Metadata METRICS_REFRESH = Metadata.builder()
            .withName("keycloak_metrics_refresh")
            .withDescription("Duration of Metrics refresh in millis")
            .withType(MetricType.GAUGE)
            .build();

    public static final String TAG_REALM = "realm";

    private final KeycloakMetricsCollector metricsCollector;

    private final MetricRegistry metricsRegistry;

    public KeycloakMetrics(MetricRegistry metricsRegistry, KeycloakMetricsCollector metricsCollector) {
        this.metricsRegistry = metricsRegistry;
        this.metricsCollector = metricsCollector;
    }

    public void init() {
        registerServerVersionGauge(metricsRegistry);
        registerDynamicMetrics(metricsRegistry);
    }

    private void registerDynamicMetrics(MetricRegistry metricsRegistry) {
        metricsRegistry.register(METRICS_REFRESH, (Gauge<Double>) () -> metricsCollector.getCustomMetric(METRICS_REFRESH));
    }

    /**
     * Registers a dummy gauge with value 0 that piggy-backs the current Keycloak version as a label.
     *
     * @param metricsRegistry
     */
    private void registerServerVersionGauge(MetricRegistry metricsRegistry) {
        metricsRegistry.register(SERVER_VERSION, (Gauge<Double>) () -> 0.0, tag("version", Version.VERSION));
    }

    public static String customMetricName(String... qualifier) {
        return String.join("_", qualifier);
    }


    public static Tag tag(String name, String value) {
        return new Tag(name, value);
    }
}
