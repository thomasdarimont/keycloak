package org.keycloak.quarkus.runtime.services.health.realm;

import org.keycloak.health.HealthIndicator;
import org.keycloak.health.HealthIndicatorFactory;
import org.keycloak.models.KeycloakSession;

public class LdapHealthIndicatorFactory implements HealthIndicatorFactory {

    @Override
    public HealthIndicator create(KeycloakSession session) {
        return new LdapHealthIndicator(session, getId(), 500);
    }

    @Override
    public String getId() {
        return "ldap";
    }
}
