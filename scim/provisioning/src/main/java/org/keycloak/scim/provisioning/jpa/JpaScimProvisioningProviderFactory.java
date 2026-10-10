package org.keycloak.scim.provisioning.jpa;

import jakarta.persistence.EntityManager;

import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.scim.provisioning.ScimProvisioning;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.model.ScimProvisioningProviderFactory;

/** The only implementation: the outbox lives in Keycloak's own database. */
public class JpaScimProvisioningProviderFactory implements ScimProvisioningProviderFactory, EnvironmentDependentProviderFactory {

    public static final String PROVIDER_ID = "jpa";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public ScimProvisioningProvider create(KeycloakSession session) {
        EntityManager em = session.getProvider(JpaConnectionProvider.class).getEntityManager();
        return new JpaScimProvisioningProvider(session, em);
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public boolean isSupported(Config.Scope config) {
        return ScimProvisioning.isEnabled();
    }
}
