package org.keycloak.scim.provisioning.component;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.UserStorageProvider;

/**
 * The destination as a user-storage provider. It implements <b>no capability
 * interface</b> — no user lookup, query or registration — so Keycloak's user storage manager has
 * nothing to call on the login path. The only callbacks it receives are the {@code preRemove}
 * hooks.
 */
public class DestinationProvider implements UserStorageProvider {
    private final KeycloakSession session;
    private final ComponentModel model;

    public DestinationProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    /**
     * A group is being deleted. On 26.8.0 Keycloak calls this <em>after</em> it has deleted the local
     * memberships, so the members it can still list are federated ones only; the work is done from
     * {@code GroupRemovedEvent}, which is published before (see {@code ModelEventBridge}). Kept as a
     * backstop for members this hook can still see. Resync remains the final backstop.
     */
    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
        GroupRemoval.membersLeaving(session, realm, model.getId(), group);
    }

    /** Roles are not a scope rule. */
    @Override
    public void preRemove(RealmModel realm, RoleModel role) {}

    @Override
    public void close() {}
}
