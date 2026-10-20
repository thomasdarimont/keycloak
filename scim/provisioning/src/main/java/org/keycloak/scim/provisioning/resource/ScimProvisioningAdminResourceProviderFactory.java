package org.keycloak.scim.provisioning.resource;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.scim.provisioning.ScimProvisioning;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProvider;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProviderFactory;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

/**
 * Mounts {@link ScimProvisioningAdminResource} at {@code /admin/realms/{realm}/scim-provisioning},
 * so {@code kcadm.sh get scim-provisioning/status -r <realm>} resolves.
 */
public class ScimProvisioningAdminResourceProviderFactory
        implements AdminRealmResourceProviderFactory, AdminRealmResourceProvider, EnvironmentDependentProviderFactory {
    public static final String ID = "scim-provisioning";

    @Override
    public AdminRealmResourceProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public Object getResource(
            KeycloakSession session,
            RealmModel realm,
            AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent) {
        return new ScimProvisioningAdminResource(session, realm, auth, adminEvent);
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return ScimProvisioning.isEnabled();
    }
}
