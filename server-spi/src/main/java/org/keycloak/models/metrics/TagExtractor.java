package org.keycloak.models.metrics;

@FunctionalInterface
public interface TagExtractor<S> {

    String[] extractTags(S source);
}
