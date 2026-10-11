package org.keycloak.scim.provisioning.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.keycloak.scim.provisioning.client.ErrorClass;
import org.keycloak.scim.provisioning.component.Destination;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * Meters on Micrometer's global registry, which Keycloak's metrics endpoint already scrapes
 *: no new scrape target. Prometheus names: {@code keycloak_scim_provisioning_backlog},
 * {@code keycloak_scim_provisioning_delivered_total}, {@code keycloak_scim_provisioning_failed_total}, {@code
 * keycloak_scim_provisioning_dead_total}, {@code keycloak_scim_provisioning_reconcile_mismatches}, {@code
 * keycloak_scim_provisioning_delivery_seconds}. Tags: {@code realm} (realm id), {@code destination} (component
 * name).
 */
public final class Meters {
    private static final Map<List<String>, AtomicLong> BACKLOG = new ConcurrentHashMap<>();
    private static final Map<List<String>, AtomicLong> MISMATCHES = new ConcurrentHashMap<>();

    private Meters() {}

    private static MeterRegistry registry() {
        return Metrics.globalRegistry;
    }

    /** Pending entries of a destination, as last counted. */
    public static void backlog(String realmId, Destination d, long pending) {
        gauge(BACKLOG, "keycloak.scim.provisioning.backlog", "Entries waiting for delivery", realmId, d).set(pending);
    }

    public static void reconcileMismatches(String realmId, Destination d, long mismatches) {
        gauge(
                        MISMATCHES,
                        "keycloak.scim.provisioning.reconcile.mismatches",
                        "Mismatches found by the last reconcile",
                        realmId,
                        d)
                .set(mismatches);
    }

    public static void delivered(String realmId, Destination d) {
        Counter.builder("keycloak.scim.provisioning.delivered")
                .description("Deliveries that changed the destination")
                .tag("realm", realmId)
                .tag("destination", name(d))
                .register(registry())
                .increment();
    }

    public static void failed(String realmId, Destination d, ErrorClass c) {
        Counter.builder("keycloak.scim.provisioning.failed")
                .description("Failed SCIM calls by error class")
                .tag("realm", realmId)
                .tag("destination", name(d))
                .tag("class", c.code())
                .register(registry())
                .increment();
    }

    /** {@code deprovision=true} is the security alarm: someone may keep access. */
    public static void dead(String realmId, Destination d, boolean deprovision) {
        Counter.builder("keycloak.scim.provisioning.dead")
                .description("Entries dead-lettered after their last attempt")
                .tag("realm", realmId)
                .tag("destination", name(d))
                .tag("op", deprovision ? "deprovision" : "upsert")
                .register(registry())
                .increment();
    }

    public static void deliverySeconds(String realmId, Destination d, long nanos) {
        Timer.builder("keycloak.scim.provisioning.delivery")
                .description("Time spent in SCIM calls per delivery")
                .tag("realm", realmId)
                .tag("destination", name(d))
                .register(registry())
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    private static AtomicLong gauge(
            Map<List<String>, AtomicLong> map,
            String meter,
            String description,
            String realmId,
            Destination d) {
        return map.computeIfAbsent(
                List.of(realmId, d.id()),
                k -> {
                    AtomicLong v = new AtomicLong();
                    Gauge.builder(meter, v, AtomicLong::get)
                            .description(description)
                            .tag("realm", realmId)
                            .tag("destination", name(d))
                            .register(registry());
                    return v;
                });
    }

    private static String name(Destination d) {
        return d.name() == null ? d.id() : d.name();
    }
}
