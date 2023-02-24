package org.keycloak.quarkus.runtime.integration.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import org.hibernate.jpa.QueryHints;
import org.jetbrains.annotations.NotNull;
import org.keycloak.events.Event;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.DefaultMetricsManager;
import org.keycloak.models.metrics.KeycloakMetric;
import org.keycloak.metrics.KeycloakMetricsProvider;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.metrics.KeycloakMetricsContext;
import org.keycloak.models.metrics.RealmReference;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.ProviderEvent;
import org.keycloak.provider.ProviderEventListener;

import javax.enterprise.inject.spi.CDI;
import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class QuarkusMetricsManager extends DefaultMetricsManager {

    private final List<KeycloakMetricsProvider> metricProviders;

    private final MetricsProviderEventListener providerEventListener;

    private List<KeycloakMetric> instanceMetrics;

    private List<KeycloakMetric<RealmModel>> realmMetrics;

    private List<KeycloakMetric<Event>> userEventMetrics;

    private List<KeycloakMetric<AdminEvent>> adminEventMetrics;

    public QuarkusMetricsManager(KeycloakSessionFactory keycloakSessionFactory, List<KeycloakMetricsProvider> metricProviders) {
        super(keycloakSessionFactory);
        this.metricProviders = metricProviders;
        this.providerEventListener = new MetricsProviderEventListener();
    }

    public MetricsProviderEventListener getProviderEventListener() {
        return providerEventListener;
    }

    public void init() {

        List<KeycloakMetric> instanceMetrics = new ArrayList<>();
        List<KeycloakMetric<RealmModel>> realmMetrics = new ArrayList<>();
        List<KeycloakMetric<Event>> userEventMetrics = new ArrayList<>();
        List<KeycloakMetric<AdminEvent>> adminEventMetrics = new ArrayList<>();

        for (KeycloakMetricsProvider provider : metricProviders) {
            for (KeycloakMetric metric : provider.createMetrics()) {
                switch (metric.getType()) {
                    case INSTANCE:
                        instanceMetrics.add(metric);
                        break;
                    case REALM:
                        realmMetrics.add(metric);
                        break;
                    case USER_EVENT:
                        userEventMetrics.add(metric);
                        break;
                    case ADMIN_EVENT:
                        adminEventMetrics.add(metric);
                        break;
                }

                keycloakMetricMap.put(metric.getName(), metric);
            }
        }

        MeterRegistry meterRegistry = getMeterRegistry();

        this.instanceMetrics = instanceMetrics;
        this.realmMetrics = realmMetrics;
        this.userEventMetrics = userEventMetrics;
        this.adminEventMetrics = adminEventMetrics;

        registerInstanceMetrics(meterRegistry, instanceMetrics);

        // TODO instead of creating the metrics at startup, create the metrics lazily if requested per realm
        registerRealmMetrics(meterRegistry, realmMetrics, userEventMetrics, adminEventMetrics);
    }

    public void registerRealmMetrics(MeterRegistry meterRegistry, List<KeycloakMetric<RealmModel>> realmMetrics, List<KeycloakMetric<Event>> userEventMetrics, List<KeycloakMetric<AdminEvent>> adminEventMetrics) {

        List<RealmReference> realms = new ArrayList<>();
        {
            EntityManager em = CDI.current().select(EntityManager.class).get();
            List<Object[]> result = em.createQuery("select r.id, r.name from RealmEntity r") //
                    .setHint(QueryHints.HINT_READONLY, true) //
                    .getResultList();
            result.forEach(values -> {
                realms.add(new RealmReference(String.valueOf(values[0]), String.valueOf(values[1])));
            });
        }

        KeycloakModelUtils.runJobInTransaction(keycloakSessionFactory, session -> {
            for (RealmReference realmReference : realms) {
                RealmModel realm = session.realms().getRealm(realmReference.getId());
                if (realm.getEventsListenersStream().noneMatch(KeycloakMetricsListenerFactory.PROVIDER_ID::equals)) {
                    // skip creation of counters for realm with no keycloak-metrics events lister
                    continue;
                }
                registerRealmMetrics(meterRegistry, realmMetrics, userEventMetrics, adminEventMetrics, realm);
            }
        });
    }

    @NotNull
    private MeterRegistry getMeterRegistry() {
        return Metrics.globalRegistry;
    }

    protected void registerInstanceMetrics(MeterRegistry meterRegistry, List<KeycloakMetric> instanceMetrics) {

        KeycloakMetricsContext context = new KeycloakMetricsContext() {
            @Override
            public RealmReference getRealmReference() {
                return null;
            }

            @Override
            public KeycloakSessionFactory getSessionFactory() {
                return keycloakSessionFactory;
            }
        };

        for (KeycloakMetric metric : instanceMetrics) {
            // custom instance metrics are handled as gauges by default
            Gauge.builder(metric.getName(), () -> metric.getMetricComputation().compute(context)) //
                    .description(metric.getDescription()) //
                    .tags(Tags.of(metric.getDefaultTags())) //
                    .register(meterRegistry);
        }
    }

    protected void registerRealmMetrics(MeterRegistry meterRegistry, List<KeycloakMetric<RealmModel>> realmMetrics, List<KeycloakMetric<Event>> userEventMetrics, List<KeycloakMetric<AdminEvent>> adminEventMetrics, RealmModel realm) {

        KeycloakMetricsContext context = new KeycloakMetricsContext() {
            @Override
            public RealmReference getRealmReference() {
                return new RealmReference(realm.getId(), realm.getName());
            }

            @Override
            public KeycloakSessionFactory getSessionFactory() {
                return keycloakSessionFactory;
            }

        };

        for (KeycloakMetric<RealmModel> metric : realmMetrics) {
            String[] tags = metric.getTagsExtractor().extractTags(realm);
            // custom realm metrics are handled as gauges by default
            Gauge.builder(metric.getName(), () -> metric.getMetricComputation().compute(context)) //
                    .description(metric.getDescription()) //
                    .tags(Tags.of(tags)) //
                    .register(meterRegistry);
        }

        // custom user event metrics are handled as counters by default
        for (KeycloakMetric<Event> metric : userEventMetrics) {
            Counter.builder(metric.getName()) //
                    .description(metric.getDescription()) //
                    .tag("realmId", realm.getId()) //
                    .tag("realm", realm.getName()) //
                    .register(meterRegistry);
        }

        // custom admin event metrics are handled as counters by default
        for (KeycloakMetric<AdminEvent> metric : adminEventMetrics) {
            Counter.builder(metric.getName()) //
                    .description(metric.getDescription()) //
                    .tag("realmId", realm.getId()) //
                    .tag("realm", realm.getName()) //
                    .register(meterRegistry);
        }
    }

    protected void unregisterRealmMetrics(MeterRegistry meterRegistry, List<KeycloakMetric<RealmModel>> realmMetrics, List<KeycloakMetric<Event>> userEventMetrics, List<KeycloakMetric<AdminEvent>> adminEventMetrics, RealmModel realm) {
        // TODO implement removal of metrics on realm deletion
    }

    @Override
    public void recordMetric(String name, Supplier<Number> valueSupplier, String... tags) {
        getMeterRegistry().counter(name, tags).increment(valueSupplier.get().doubleValue());
    }

    private class MetricsProviderEventListener implements ProviderEventListener {

        @Override
        public void onEvent(ProviderEvent event) {

            if (event instanceof PostMigrationEvent) {
                // delay metrics initialization once keycloak components are ready
                init();
            } else if (event instanceof RealmModel.RealmPostCreateEvent) {
                // TODO only create events if keycloak-metrics event listener is installed.
                RealmModel realm = ((RealmModel.RealmPostCreateEvent) event).getCreatedRealm();
                registerRealmMetrics(getMeterRegistry(), realmMetrics, userEventMetrics, adminEventMetrics, realm);
                // register realm metrics
            } else if (event instanceof RealmModel.RealmRemovedEvent) {
                // remove realm metrics
                RealmModel realm = ((RealmModel.RealmRemovedEvent) event).getRealm();
                unregisterRealmMetrics(getMeterRegistry(), realmMetrics, userEventMetrics, adminEventMetrics, realm);
            }
        }
    }

}
