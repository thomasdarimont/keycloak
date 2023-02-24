package org.keycloak.models.metrics;


public class KeycloakMetric<S> {

    private final String name;

    private final String description;

    private final Type type;

    private final MetricComputation metricComputation;

    private final String[] defaultTags;

    private final TagExtractor<S> tagsExtractor;

    public KeycloakMetric(String name, String description, Type type, MetricComputation metricComputation, TagExtractor<S> tagsExtractor) {
        this(name, description, type, metricComputation, tagsExtractor, new String[0]);
    }

    public KeycloakMetric(String name, String description, Type type, MetricComputation metricComputation, TagExtractor<S> tagsExtractor, String... defaultTags) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.metricComputation = metricComputation;
        this.tagsExtractor = tagsExtractor;
        this.defaultTags = defaultTags.clone();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public MetricComputation getMetricComputation() {
        return metricComputation;
    }

    public TagExtractor<S> getTagsExtractor() {
        return tagsExtractor;
    }

    public String[] getDefaultTags() {
        return defaultTags.clone();
    }

}
