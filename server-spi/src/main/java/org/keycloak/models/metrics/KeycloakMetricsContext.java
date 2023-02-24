package org.keycloak.models.metrics;

import org.keycloak.models.KeycloakSessionFactory;

import java.util.Map;

public interface KeycloakMetricsContext {

    RealmReference getRealmReference();

    KeycloakSessionFactory getSessionFactory();
}
