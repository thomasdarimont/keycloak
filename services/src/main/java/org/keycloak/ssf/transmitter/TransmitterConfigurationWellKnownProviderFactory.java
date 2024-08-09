package org.keycloak.ssf.transmitter;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.wellknown.WellKnownProvider;
import org.keycloak.wellknown.WellKnownProviderFactory;

public class TransmitterConfigurationWellKnownProviderFactory implements WellKnownProviderFactory {

    @Override
    public String getId() {
        return "ssf-configuration";
    }

    @Override
    public WellKnownProvider create(KeycloakSession session) {
        return new TransmitterConfigurationWellKnownProvider(session);
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
