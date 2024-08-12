package org.keycloak.protocol.ssf;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.wellknown.WellKnownProvider;
import org.keycloak.wellknown.WellKnownProviderFactory;

public class SsfConfigurationWellKnownProviderFactory implements WellKnownProviderFactory {

    @Override
    public String getId() {
        return "ssf-configuration";
    }

    @Override
    public WellKnownProvider create(KeycloakSession session) {
        return new SsfConfigurationWellKnownProvider(session);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }
}
