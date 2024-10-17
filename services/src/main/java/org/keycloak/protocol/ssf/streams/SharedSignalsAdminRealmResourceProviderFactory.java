package org.keycloak.protocol.ssf.streams;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProvider;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProviderFactory;

public class SharedSignalsAdminRealmResourceProviderFactory implements AdminRealmResourceProviderFactory {

    @Override
    public String getId() {
        return "ssf";
    }

    @Override
    public AdminRealmResourceProvider create(KeycloakSession session) {
        return new SharedSignalsAdminRealmResourceProvider(session);
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
