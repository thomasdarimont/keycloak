package org.keycloak.quarkus.runtime.integration.metrics;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.metrics.KeycloakMetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class KeycloakMetricsListener implements EventListenerProvider {

    private final KeycloakSession session;

    public KeycloakMetricsListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {

        KeycloakMetric metric = session.metrics().getMetric(getMetricName(event));
        if (metric != null) {
            recordCustomEvent(metric, event.getRealmId(), event);
        } else {
            recordGenericEvent(getMetricName(event), event.getRealmId(), event);
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {

        KeycloakMetric metric = session.metrics().getMetric(getMetricName(event));
        if (metric != null) {
            recordCustomEvent(metric, event.getRealmId(), event);
        } else {
            recordGenericEvent(getMetricName(event), event.getRealmId(), event);
        }
    }

    private void recordCustomEvent(KeycloakMetric metric, String realmId, Object event) {

        String[] tags = metric.getTagsExtractor().extractTags(event);
        tags = Arrays.copyOf(tags, tags.length + 2);
        tags[tags.length - 2] = "realm";
        tags[tags.length - 1] = resolveRealmName(realmId);
        session.metrics().recordMetric(metric.getName(), () -> 1, tags);
    }

    private void recordGenericEvent(String metricName, String realmId, Object event) {
        String[] tags = getDefaultTags(event);
        session.metrics().recordMetric(metricName, () -> 1, tags);
    }

    private String[] getDefaultTags(Object maybeEvent) {

        List<String> tags = new ArrayList<>();

        String realmId = null;
        if (maybeEvent instanceof Event) {
            Event event = (Event) maybeEvent;
            String value;
            if ((value = event.getRealmId()) != null) {
                tags.add("realmId");
                tags.add(value);
                realmId = value;
            }
            if ((value = event.getError()) != null) {
                tags.add("error");
                tags.add(value);
            }
            if ((value = event.getClientId()) != null) {
                tags.add("clientId");
                tags.add(value);
            }
        } else if (maybeEvent instanceof AdminEvent) {
            AdminEvent event = (AdminEvent) maybeEvent;
            String value;
            if ((value = event.getRealmId()) != null) {
                tags.add("realmId");
                tags.add(value);
                realmId = value;
            }
            if ((value = event.getError()) != null) {
                tags.add("error");
                tags.add(value);
            }
            if ((value = event.getResourcePath()) != null) {
                tags.add("path");
                tags.add(value);
            }
            if (event.getOperationType() != null) {
                tags.add("operation");
                tags.add(event.getOperationType().name());
            }
            if (event.getResourceType() != null) {
                tags.add("resourceType");
                tags.add(event.getResourceType().name());
            }
        }

        if (realmId != null) {
            tags.add("realm");
            tags.add(resolveRealmName(realmId));
        }

        return tags.toArray(String[]::new);
    }

    protected String getMetricName(AdminEvent event) {
        return "keycloak_adminevent_" + event.getResourceTypeAsString().toLowerCase(Locale.ENGLISH) + "_total";
    }

    protected String getMetricName(Event event) {
        return "keycloak_userevent_" + event.getType().name().toLowerCase(Locale.ENGLISH) + "_total";
    }

    @Override
    public void close() {
        // NOOP
    }

    private String resolveRealmName(String realmId) {
        RealmModel realm = session.realms().getRealm(realmId);
        return realm.getName();
    }
}
