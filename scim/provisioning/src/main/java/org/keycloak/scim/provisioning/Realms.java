package org.keycloak.scim.provisioning;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * Background sessions (drainer, reconcile, resync) are not bound to a realm, and Keycloak 26.8's
 * user lookups require one in the session context.
 */
public final class Realms {
    private Realms() {}

    /** The realm, bound to the session's context; null if the realm no longer exists. */
    public static RealmModel bind(KeycloakSession session, String realmId) {
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm != null) session.getContext().setRealm(realm);
        return realm;
    }
}
