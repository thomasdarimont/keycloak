package org.keycloak.services.metrics;

import org.eclipse.microprofile.metrics.Metadata;
import org.keycloak.models.RealmModel;

public interface MetricUpdater {

    void updateMetricValue(Metadata metric, RealmModel realm, Number value);
}
