package org.keycloak.quarkus.runtime.services.health.realm;

import org.keycloak.health.HealthIndicator;
import org.keycloak.health.HealthIndicatorFactory;
import org.keycloak.models.KeycloakSession;

public class EmailHealthIndicatorFactory implements HealthIndicatorFactory {

    @Override
    public String getId() {
        return "email";
    }

    @Override
    public HealthIndicator create(KeycloakSession session) {
        return new EmailHealthIndicator(getId(), 500);
    }
}
