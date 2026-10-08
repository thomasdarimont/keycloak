package org.keycloak.authentication.clientaccess;

import org.keycloak.Config;
import org.keycloak.component.ComponentFactory;
import org.keycloak.models.KeycloakSessionFactory;

public interface ClientAccessPolicyFactory extends ComponentFactory<ClientAccessPolicy, ClientAccessPolicy> {

    @Override
    default void init(Config.Scope config) {
    }

    @Override
    default void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    default void close() {
    }
}
