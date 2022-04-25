package org.keycloak.health;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderFactory;

public interface HealthIndicatorFactory extends ProviderFactory<HealthIndicator> {

    @Override
    default void init(Config.Scope config) {
        // NOOP
    }

    @Override
    default void postInit(KeycloakSessionFactory factory) {
        // NOOP
    }

    @Override
    default void close() {
        // NOOP
    }
}