package org.keycloak.scim.provisioning.component;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.reconcile.Resync;

/** What creating, changing and removing a destination does to the outbox. */
final class Lifecycle {
    private Lifecycle() {}

    static void created(KeycloakSession session, RealmModel realm, ComponentModel model) {
        Resync.afterCommit(session, realm.getId(), model.getId());
    }

    static void updated(
            KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        ScimProvisioningProvider outbox = session.getProvider(ScimProvisioningProvider.class);
        long now = System.currentTimeMillis();
        outbox.ensureHealth(realm.getId(), newModel.getId(), now);
        // a reconfigured destination (a rotated token, a fixed URL, enabled again) is tried again at
        // once: its rows were deferred to the end of the backoff, or while it was disabled
        outbox.clearBackoff(realm.getId(), newModel.getId());
        outbox.makeDue(realm.getId(), newModel.getId());
        // scope, mapping and the enabled switch are not visible as Keycloak events
        Resync.afterCommit(session, realm.getId(), newModel.getId());
    }

    static void removed(KeycloakSession session, RealmModel realm, ComponentModel model) {
        session.getProvider(ScimProvisioningProvider.class).removeDestination(realm.getId(), model.getId());
    }
}
