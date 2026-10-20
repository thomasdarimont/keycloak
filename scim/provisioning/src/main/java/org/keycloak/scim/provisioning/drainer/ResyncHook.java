package org.keycloak.scim.provisioning.drainer;

import org.keycloak.models.KeycloakSessionFactory;

/** Lets the sweeper ask for a resync without a package cycle at class-load time. */
final class ResyncHook {
    private ResyncHook() {}

    static void request(KeycloakSessionFactory factory, String realmId, String destinationId) {
        org.keycloak.scim.provisioning.reconcile.Resync.request(factory, realmId, destinationId);
    }
}
