package org.keycloak.services.metrics;

import org.eclipse.microprofile.metrics.Metadata;

public interface MetricAccessor {

    Double getMetricValue(Metadata metric);

    Double getMetricValue(String metricKey);
}
