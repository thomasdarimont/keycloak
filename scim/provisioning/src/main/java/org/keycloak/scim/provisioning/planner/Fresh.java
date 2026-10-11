package org.keycloak.scim.provisioning.planner;

import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserProvider;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.storage.StoreManagers;

/**
 * Users and groups read past Keycloak's cache, as the cache itself loads them (federation
 * included). The cache is invalidated only after a change's transaction has committed, and the
 * drainer, woken by that commit, can read in between: through the cache it would plan from the
 * state before the change, find it unchanged and settle the change's row without sending it.
 */
final class Fresh {
    private Fresh() {}

    static UserProvider users(KeycloakSession session) {
        return session.getProvider(DatastoreProvider.class) instanceof StoreManagers m
                ? m.userStorageManager()
                : session.users();
    }

    static GroupProvider groups(KeycloakSession session) {
        return session.getProvider(DatastoreProvider.class) instanceof StoreManagers m
                ? m.groupStorageManager()
                : session.groups();
    }
}
