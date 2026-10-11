package org.keycloak.tests.providers.scim.provisioning;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Test only, in Keycloak's custom test providers; never shipped. {@code /realms/{realm}/scim-provisioning-test/...}: a deterministic
 * rolled-back user change and a DB-agnostic view of the outbox rows.
 */
public class ScimProvisioningTestResourceProviderFactory implements RealmResourceProviderFactory {
    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new RealmResourceProvider() {
            @Override
            public Object getResource() {
                return new ScimProvisioningTestResource(session);
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return "scim-provisioning-test";
    }
}
